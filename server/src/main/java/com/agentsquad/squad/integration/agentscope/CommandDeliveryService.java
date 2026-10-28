package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.domain.DeliveryRow;
import com.agentsquad.squad.conversation.domain.RuntimeCommandAction;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import com.agentsquad.squad.conversation.service.TaskControlService;
import com.agentsquad.squad.runtime.service.TaskFinalizerService;
import com.agentsquad.squad.runtime.service.WorkerRecoveryService;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 持久命令投递器。领取和提交分属两个持久系统，故任何失败都用相同命令 ID 与输入重试。
 *
 * <p>投递租约只控制平台记录更新；AgentScope 原生日志自己负责 writer lease 和原生幂等。
 * SUBMIT 投递新回合，STOP 投递中断意图，RESUME 投递恢复意图。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CommandDeliveryService {

    private static final int LEASE_SECONDS = 60;
    private static final String ALREADY_SETTLED = "Only the latest unfinished task can continue";
    private static final String CONTINUATION_QUEUED = "Continuation already queued";
    private static final int MAX_RESUME_ATTEMPTS = 5;

    @Resource
    private DeliveryMapper deliveryMapper;

    @Resource
    private NativeSessionGateway nativeSessionGateway;

    @Resource
    private TaskFinalizerService taskFinalizerService;

    @Resource
    private TaskControlService taskControlService;

    @Resource
    private WorkerRecoveryService workerRecoveryService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 周期性寻找到期命令；多实例可并发扫描，只有一个能原子领取。
     */
    @Scheduled(fixedDelayString = "${agentsquad.delivery.poll-delay-ms:1000}")
    public void deliverDue() {
        List<String> commandIds = deliveryMapper.findDueIds(now());
        for (String commandId : commandIds) {
            deliver(commandId);
        }
    }

    /**
     * 单条投递不跨数据库事务等待模型；失败按有界退避重新排队。
     */
    public void deliver(String commandId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime claimedAt = now();
        if (deliveryMapper.claim(commandId, token, claimedAt,
                claimedAt.plusSeconds(LEASE_SECONDS)) != 1) {
            return;
        }
        DeliveryRow delivery = deliveryMapper.findClaimed(commandId, token);
        if (delivery == null) {
            retry(commandId, token, 1, "BINDING_MISSING");
            return;
        }
        try {
            RuntimeCommandAction action;
            try {
                action = RuntimeCommandAction.valueOf(delivery.action());
            } catch (IllegalArgumentException exception) {
                fail(commandId, token, delivery.attempts(), "UNKNOWN_ACTION");
                return;
            }
            switch (action) {
                case SUBMIT, MASTER_SIGNAL -> deliverSubmit(commandId, token, delivery);
                case STOP -> deliverStop(commandId, token, delivery);
                case RESUME -> deliverResume(commandId, token, delivery);
                case RESPOND -> deliverRespond(commandId, token, delivery);
                case DELETE_CONVERSATION -> fail(commandId, token, delivery.attempts(), "UNDELIVERABLE_ACTION");
            }
        } catch (Exception exception) {
            retry(commandId, token, delivery.attempts(), "NATIVE_SUBMIT_FAILED");
            log.warn("原生命令提交失败，稍后按原键重试 commandId={} taskId={} errorType={}",
                    commandId, delivery.taskId(), exception.getClass().getSimpleName());
        }
    }

    private void deliverSubmit(String commandId, String token, DeliveryRow delivery) throws Exception {
        JsonNode payload = RuntimePayloads.readJson(objectMapper, delivery.payloadJson());
        String message = payload.path("message").textValue();
        if (message == null || message.isBlank()) {
            retry(commandId, token, delivery.attempts(), "INVALID_PAYLOAD");
            return;
        }
        NativeSubmissionReceipt receipt = nativeSessionGateway.submit(delivery, message);
        if (!commandId.equals(receipt.commandId())) {
            throw new IllegalStateException("原生命令收据 ID 不一致");
        }
        saveReceipt(commandId, token, delivery, receipt.turnId(), receipt.runId(), receipt.status());
    }

    /**
     * 投递中断意图；原生会话已无活跃运行时就地取消目标，不伪造等待停止。
     */
    private void deliverStop(String commandId, String token, DeliveryRow delivery) {
        boolean interrupted = nativeSessionGateway.stop(delivery);
        if (!interrupted) {
            taskFinalizerService.finalizeCancelledTask(delivery.userId(), delivery.taskId());
        }
        saveReceipt(commandId, token, delivery, null, null,
                interrupted ? "interrupted" : "no-active-run");
    }

    /**
     * 投递恢复意图；恢复前必须已存在原生回合，未受理目标先等 SUBMIT 投递成功。
     */
    private void deliverResume(String commandId, String token, DeliveryRow delivery) {
        String turnId = deliveryMapper.findSubmitTurnId(delivery.userId(), delivery.taskId(),
                delivery.bindingId());
        if (turnId == null) {
            retry(commandId, token, delivery.attempts(), "NATIVE_TURN_MISSING");
            return;
        }
        try {
            nativeSessionGateway.resume(delivery, turnId);
        } catch (IllegalStateException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.contains(CONTINUATION_QUEUED)) {
                saveReceipt(commandId, token, delivery, turnId, null, "resumed");
                return;
            }
            if (message.contains(ALREADY_SETTLED) || delivery.attempts() >= MAX_RESUME_ATTEMPTS) {
                String errorCode = message.contains(ALREADY_SETTLED)
                        ? "NATIVE_TURN_SETTLED" : "NATIVE_RESUME_REJECTED";
                if (MasterAgentIdentity.AGENT_ID.equals(delivery.agentId())) {
                    taskControlService.blockRejectedResume(delivery.userId(), delivery.taskId(),
                            commandId, token, errorCode);
                } else if (message.contains(ALREADY_SETTLED)) {
                    workerRecoveryService.discardSettledResume(commandId, token);
                } else {
                    workerRecoveryService.blockRejectedResume(delivery.userId(), delivery.taskId(),
                            delivery.bindingId(), commandId, token, errorCode);
                }
                return;
            }
            retry(commandId, token, delivery.attempts(), "NATIVE_RESUME_REJECTED");
            return;
        }
        saveReceipt(commandId, token, delivery, turnId, null, "resumed");
    }

    /**
     * 投递交互答复；先让目标离开等待态，再注入答案驱动原生回合恢复。
     * 原生请求已消失说明交互已被框架侧解析，按终局受理不再重试。
     */
    private void deliverRespond(String commandId, String token, DeliveryRow delivery) throws Exception {
        JsonNode payload = RuntimePayloads.readJson(objectMapper, delivery.payloadJson());
        String requestId = payload.path("requestId").textValue();
        String answerText = payload.path("answerText").textValue();
        if (requestId == null || requestId.isBlank() || answerText == null) {
            retry(commandId, token, delivery.attempts(), "INVALID_PAYLOAD");
            return;
        }
        taskFinalizerService.resumeFromInteraction(delivery.userId(), delivery.taskId());
        String nativeStatus = nativeSessionGateway.respond(delivery, requestId, answerText);
        saveReceipt(commandId, token, delivery, null, null, nativeStatus);
    }

    private void saveReceipt(String commandId, String token, DeliveryRow delivery,
                             String turnId, String runId, String nativeStatus) {
        int saved = deliveryMapper.saveReceipt(commandId, token, turnId, runId, nativeStatus, now());
        if (saved == 1) {
            log.info("原生命令已投递 commandId={} taskId={} action={} nativeStatus={}",
                    commandId, delivery.taskId(), delivery.action(), nativeStatus);
        } else {
            log.warn("命令收据写入时投递租约已失效 commandId={} taskId={}", commandId, delivery.taskId());
        }
    }

    private void retry(String commandId, String token, int attempts, String errorCode) {
        long delaySeconds = Math.min(300, 1L << Math.min(Math.max(attempts, 1), 8));
        deliveryMapper.retry(commandId, token, now().plusSeconds(delaySeconds), errorCode);
        log.warn("命令投递待重试 commandId={} errorCode={} delaySeconds={}",
                commandId, errorCode, delaySeconds);
    }

    private void fail(String commandId, String token, int attempts, String errorCode) {
        retry(commandId, token, Math.max(attempts, 8), errorCode);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
