package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.common.observability.ObservationSupport;
import com.agentsquad.squad.conversation.domain.DeliveryRow;
import com.agentsquad.squad.conversation.domain.CommandClaimContext;
import com.agentsquad.squad.conversation.service.RuntimeAdmissionService;
import com.agentsquad.squad.conversation.service.RuntimeAdmissionBusyException;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import com.agentsquad.squad.conversation.service.ResponseExecutionService;
import com.agentsquad.squad.conversation.service.CommandClaimService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 后台命令派发服务。数据库接手命令和 AgentScope 开始运行分属两个系统；失败重试时保持命令 ID 和输入不变，避免重复执行。
 *
 * <p>命令的限时执行资格只控制数据库里的派发记录；AgentScope 自己负责协调写入权限和防止同一命令被重复执行。
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
    private com.agentsquad.squad.runtime.service.ExecutionAuthorizationService authorization;

    @Resource
    private com.agentsquad.squad.runtime.service.ExecutionRestrictionService restrictions;

    @Resource
    private com.agentsquad.squad.conversation.service.ExecutionScopeService scopes;

    @Resource
    private ResponseExecutionService responses;

    @Resource
    private CommandClaimService claims;

    @Resource
    private DeliveryMapper deliveryMapper;

    @Resource
    private RuntimeAdmissionService admission;

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

    @Resource
    private ObservationSupport observations;

    @Value("${agentsquad.delivery.workers:4}")
    private int workers;

    @Value("${agentsquad.delivery.control-workers:2}")
    private int controlWorkers;

    private ThreadPoolExecutor deliveryExecutor;
    private ThreadPoolExecutor controlExecutor;
    private final Set<String> scheduledCommands = ConcurrentHashMap.newKeySet();

    /** 初始化后台执行线程池；线程不够用时不接手新的数据库命令。 */
    @PostConstruct
    public void initializeDelivery() {
        if (workers < 1 || controlWorkers < 1) {
            throw new IllegalArgumentException("命令投递线程数必须为正数");
        }
        deliveryExecutor = executor(workers);
        controlExecutor = executor(controlWorkers);
    }

    private ThreadPoolExecutor executor(int size) {
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "command-delivery");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 停机先停止领取，让已开始的 SDK 投递完成；超时保留原命令供租约恢复。 */
    @PreDestroy
    public void shutdownDelivery() {
        deliveryExecutor.shutdown();
        controlExecutor.shutdown();
        try {
            if (!deliveryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                deliveryExecutor.shutdownNow();
            }
            if (!controlExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                controlExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            deliveryExecutor.shutdownNow();
            controlExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 公平候选扫描后按容量派发；控制命令拥有独立容量，不被慢提交占满。 */
    @Scheduled(fixedDelayString = "${agentsquad.delivery.poll-delay-ms:1000}")
    public void deliverDue() {
        LocalDateTime scannedAt = now();
        deliveryMapper.blockUnknownPending(scannedAt);
        Set<String> commandIds = new LinkedHashSet<>(deliveryMapper.findDueControlIds(scannedAt));
        commandIds.addAll(deliveryMapper.findDueIds(scannedAt));
        for (String commandId : commandIds) {
            if (!scheduledCommands.add(commandId)) {
                continue;
            }
            try {
                CommandClaimContext context = deliveryMapper.claimContext(commandId);
                boolean control = context != null && ("STOP".equals(context.action())
                        || "RESPOND".equals(context.action()));
                (control ? controlExecutor : deliveryExecutor).execute(() -> {
                    try {
                        deliver(commandId);
                    } catch (Exception exception) {
                        log.warn("持久命令调度异常 commandId={} errorType={}",
                                commandId, exception.getClass().getSimpleName(), exception);
                    } finally {
                        scheduledCommands.remove(commandId);
                    }
                });
            } catch (RejectedExecutionException exception) {
                scheduledCommands.remove(commandId);
            } catch (Exception exception) {
                scheduledCommands.remove(commandId);
                log.warn("命令候选读取失败 commandId={}", commandId, exception);
            }
        }
    }

    /**
     * 单条投递不跨数据库事务等待模型；失败按有界退避重新排队。
     */
    public void deliver(String commandId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime claimedAt = now();
        try {
            if (!claims.claim(commandId, token, claimedAt, claimedAt.plusSeconds(LEASE_SECONDS))) {
                return;
            }
        } catch (RuntimeAdmissionBusyException exception) {
            // 等待下次公平扫描；领取事务回滚后命令仍处于原待领取状态。
            return;
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            // 用户被禁用后不应停止整个投递轮询；受理记录与原命令保持不变。
            log.debug("命令领取因用户状态被拒绝 commandId={} status={}",
                    commandId, exception.getStatusCode().value());
            return;
        }
        DeliveryRow delivery = deliveryMapper.findClaimed(commandId, token);
        if (delivery == null) {
            // 尚未调用原生 SDK，缺失 Binding 不可能产生本次原生运行。
            CommandClaimContext context = deliveryMapper.claimContext(commandId);
            if (context != null) {
                admission.releaseUnsubmitted(context.userId(), context.bindingId(), commandId);
            }
            retry(commandId, token, 1, "BINDING_MISSING");
            return;
        }
        try {
            RuntimeCommandAction action;
            try {
                action = RuntimeCommandAction.valueOf(delivery.action());
            } catch (IllegalArgumentException exception) {
                admission.releaseUnsubmitted(delivery.userId(), delivery.bindingId(), commandId);
                fail(commandId, token, delivery.attempts(), "UNKNOWN_ACTION");
                return;
            }
            if (action != RuntimeCommandAction.STOP && action != RuntimeCommandAction.DELETE_CONVERSATION) {
                String scopeId = scopes.scopeForSession(delivery.userId(), delivery.sessionId());
                authorization.prepare(delivery.userId(), scopeId);
            }
            switch (action) {
                case SUBMIT, MESSAGE_SUBMIT, MASTER_SIGNAL, TASK_DIRECTIVE -> deliverSubmit(commandId, token, delivery);
                case STOP -> deliverStop(commandId, token, delivery);
                case RESUME -> deliverResume(commandId, token, delivery);
                case RESPOND -> deliverRespond(commandId, token, delivery);
                case DELETE_CONVERSATION -> {
                    admission.releaseUnsubmitted(delivery.userId(), delivery.bindingId(), commandId);
                    fail(commandId, token, delivery.attempts(), "UNDELIVERABLE_ACTION");
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            admission.releaseUnsubmitted(delivery.userId(), delivery.bindingId(), commandId);
            fail(commandId, token, delivery.attempts(), "INVALID_PAYLOAD");
        } catch (com.agentsquad.squad.runtime.domain.ExecutionContextInvalidatedException exception) {
            restrictions.restrict(delivery.userId(), scopes.scopeForSession(delivery.userId(), delivery.sessionId()), delivery.bindingId());
        } catch (Exception exception) {
            retry(commandId, token, delivery.attempts(), "NATIVE_SUBMIT_FAILED");
            Map<String, Object> fields = commandFields(commandId, delivery);
            fields.put("errorType", observations.classify(exception));
            log.warn("AgentScope命令提交失败，将使用同一命令编号重试 {}", observations.logFields(fields));
        }
    }

    private void deliverSubmit(String commandId, String token, DeliveryRow delivery) throws Exception {
        JsonNode payload = RuntimePayloads.readJson(objectMapper, delivery.payloadJson());
        String message = payload == null ? null : payload.path("message").textValue();
        if (message == null || message.isBlank()) {
            admission.releaseUnsubmitted(delivery.userId(), delivery.bindingId(), commandId);
            fail(commandId, token, delivery.attempts(), "INVALID_PAYLOAD");
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
            if (delivery.taskId() == null) {
                responses.stopped(delivery.userId(), delivery.bindingId());
            } else {
                taskFinalizerService.finalizeCancelledTask(delivery.userId(), delivery.taskId());
            }
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
            // 未进入 SDK，恢复命令确定没有启动新的原生 Run。
            admission.releaseUnsubmitted(delivery.userId(), delivery.bindingId(), commandId);
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
                if (delivery.taskId() == null) {
                    workerRecoveryService.discardSettledResume(commandId, token);
                    if (!message.contains(ALREADY_SETTLED)) {
                        responses.rejectedResume(delivery.userId(), delivery.bindingId());
                    }
                } else if (MasterAgentIdentity.AGENT_ID.equals(delivery.agentId())) {
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
        String requestId = payload == null ? null : payload.path("requestId").textValue();
        String answerText = payload == null ? null : payload.path("answerText").textValue();
        if (requestId == null || requestId.isBlank() || answerText == null) {
            admission.releaseUnsubmitted(delivery.userId(), delivery.bindingId(), commandId);
            fail(commandId, token, delivery.attempts(), "INVALID_PAYLOAD");
            return;
        }
        if (delivery.taskId() != null) {
            taskFinalizerService.resumeFromInteraction(delivery.userId(), delivery.taskId());
        }
        String nativeStatus = nativeSessionGateway.respond(delivery, requestId, answerText);
        saveReceipt(commandId, token, delivery, null, null, nativeStatus);
    }

    private void saveReceipt(String commandId, String token, DeliveryRow delivery,
                             String turnId, String runId, String nativeStatus) {
        int saved = deliveryMapper.saveReceipt(commandId, token, turnId, runId, nativeStatus, now());
        if (saved == 1) {
            if ("rejected".equals(nativeStatus)) {
                // SDK 已明确拒绝受理，未生成新的原生运行。
                admission.releaseUnsubmitted(delivery.userId(), delivery.bindingId(), commandId);
            } else {
                admission.bindReceipt(delivery.userId(), delivery.bindingId(),
                        commandId, delivery.action(), turnId);
            }
            Map<String, Object> fields = commandFields(commandId, delivery);
            fields.put("nativeStatus", nativeStatus);
            log.info("原生命令已投递 {}", observations.logFields(fields));
        } else {
            log.warn("命令收据写入时投递租约已失效 {}",
                    observations.logFields(commandFields(commandId, delivery)));
        }
    }

    /**
     * 组装命令投递日志的公共字段；未关联任务的命令不输出空 taskId。
     */
    private Map<String, Object> commandFields(String commandId, DeliveryRow delivery) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("commandId", commandId);
        if (delivery.taskId() != null) {
            fields.put("taskId", delivery.taskId());
        }
        fields.put("action", delivery.action());
        fields.put("attempt", delivery.attempts());
        return fields;
    }

    private void retry(String commandId, String token, int attempts, String errorCode) {
        long delaySeconds = Math.min(300, 1L << Math.min(Math.max(attempts, 1), 8));
        deliveryMapper.retry(commandId, token, now().plusSeconds(delaySeconds), errorCode);
        log.warn("命令投递待重试 {}", observations.logFields(Map.of(
                "commandId", commandId, "errorCode", errorCode,
                "attempt", attempts, "delayMs", delaySeconds * 1000)));
    }

    private void fail(String commandId, String token, int attempts, String errorCode) {
        int blocked = deliveryMapper.blockInvalid(commandId, token, errorCode);
        if (blocked == 1) {
            log.warn("非法命令已阻断 {}", observations.logFields(Map.of(
                    "commandId", commandId, "errorCode", errorCode, "attempt", attempts)));
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
