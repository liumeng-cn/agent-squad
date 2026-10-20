package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.DeliveryRow;
import com.agentsquad.squad.runtime.repository.DeliveryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>投递租约只控制平台记录更新；AgentScope 原生日志自己负责 writer lease 和原生幂等。</p>
 */
@Service
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CommandDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(CommandDeliveryService.class);
    private static final int LEASE_SECONDS = 60;

    @Resource
    private DeliveryMapper deliveryMapper;

    @Resource
    private NativeSessionGateway nativeSessionGateway;

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
            JsonNode payload = objectMapper.readTree(delivery.payloadJson());
            String message = payload.path("message").textValue();
            if (message == null || message.isBlank()) {
                retry(commandId, token, delivery.attempts(), "INVALID_PAYLOAD");
                return;
            }
            NativeSubmissionReceipt receipt = nativeSessionGateway.submit(delivery, message);
            if (!commandId.equals(receipt.commandId())) {
                throw new IllegalStateException("原生命令收据 ID 不一致");
            }
            int saved = deliveryMapper.saveReceipt(commandId, token, receipt.turnId(), receipt.runId(),
                    receipt.status(), now());
            if (saved == 1) {
                LOG.info("原生命令已提交 commandId={} taskId={} nativeStatus={}",
                        commandId, delivery.taskId(), receipt.status());
            } else {
                LOG.warn("命令收据写入时投递租约已失效 commandId={} taskId={}", commandId, delivery.taskId());
            }
        } catch (Exception exception) {
            retry(commandId, token, delivery.attempts(), "NATIVE_SUBMIT_FAILED");
            LOG.warn("原生命令提交失败，稍后按原键重试 commandId={} taskId={} errorType={}",
                    commandId, delivery.taskId(), exception.getClass().getSimpleName());
        }
    }

    private void retry(String commandId, String token, int attempts, String errorCode) {
        long delaySeconds = Math.min(300, 1L << Math.min(Math.max(attempts, 1), 8));
        deliveryMapper.retry(commandId, token, now().plusSeconds(delaySeconds), errorCode);
        LOG.warn("命令投递待重试 commandId={} errorCode={} delaySeconds={}",
                commandId, errorCode, delaySeconds);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
