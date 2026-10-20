package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.domain.CommandRow;
import com.agentsquad.squad.runtime.domain.TaskRow;
import com.agentsquad.squad.runtime.dto.CommandReceipt;
import com.agentsquad.squad.runtime.repository.BindingMapper;
import com.agentsquad.squad.runtime.repository.CommandMapper;
import com.agentsquad.squad.runtime.repository.TaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户目标控制命令受理服务。负责取消与恢复意图的幂等持久化，不直接操作框架运行。
 *
 * <p>控制命令与任务状态变更在同一事务中提交；Bridge 后续按命令投递并执行 SDK 侧合作停止或恢复。</p>
 */
@Service
@Slf4j
public class TaskControlService {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED");
    private static final Set<String> RESUMABLE_STATUSES = Set.of(
            "WAITING_INPUT", "WAITING_APPROVAL", "RECOVERY_BLOCKED", "RECONCILING");

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private BindingMapper bindingMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 提交取消命令：校验归属与版本、按幂等键去重、撤销未送达业务命令并写入 STOP 命令。
     */
    @Transactional
    public CommandReceipt cancel(String userId, String taskId, String idempotencyKey, long expectedVersion) {
        validateIdempotencyKey(idempotencyKey);
        TaskRow task = taskMapper.lockOwned(userId, taskId);
        if (task == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        String scopeKey = "task.cancel:" + taskId;
        String payloadJson = toJson(Map.of("action", "STOP", "expectedVersion", expectedVersion));
        String payloadHash = sha256(payloadJson);
        CommandRow prior = commandMapper.findByKey(userId, scopeKey, idempotencyKey);
        if (prior != null) {
            if (!prior.payloadHash().equals(payloadHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return toReceipt(prior);
        }
        if (TERMINAL_STATUSES.contains(task.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已结束，不可取消");
        }
        if (task.version() != expectedVersion) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标版本已变化");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (taskMapper.updateCancelling(userId, taskId, expectedVersion, now) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标版本已变化");
        }
        commandMapper.cancelPendingByTask(userId, taskId);
        String commandId = UUID.randomUUID().toString();
        String bindingId = requireMasterBinding(userId, taskId);
        commandMapper.insertControl(commandId, userId, scopeKey, idempotencyKey, payloadHash,
                taskId, bindingId, "STOP", payloadJson, now);
        log.info("取消命令受理成功 taskId={} commandId={} userId={}", taskId, commandId, userId);
        return new CommandReceipt(commandId, taskId, "PENDING", null, null);
    }

    /**
     * 提交恢复命令：校验归属、版本与可恢复状态，按幂等键去重并写入 RESUME 命令。
     */
    @Transactional
    public CommandReceipt resume(String userId, String taskId, String idempotencyKey, long expectedVersion) {
        validateIdempotencyKey(idempotencyKey);
        TaskRow task = taskMapper.lockOwned(userId, taskId);
        if (task == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        String scopeKey = "task.resume:" + taskId;
        String payloadJson = toJson(Map.of("action", "RESUME", "expectedVersion", expectedVersion));
        String payloadHash = sha256(payloadJson);
        CommandRow prior = commandMapper.findByKey(userId, scopeKey, idempotencyKey);
        if (prior != null) {
            if (!prior.payloadHash().equals(payloadHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return toReceipt(prior);
        }
        if (TERMINAL_STATUSES.contains(task.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已结束，不可恢复");
        }
        if (!RESUMABLE_STATUSES.contains(task.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "当前状态不可恢复");
        }
        if (task.version() != expectedVersion) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标版本已变化");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (taskMapper.updateResuming(userId, taskId, expectedVersion) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标版本已变化");
        }
        String commandId = UUID.randomUUID().toString();
        String bindingId = requireMasterBinding(userId, taskId);
        commandMapper.insertControl(commandId, userId, scopeKey, idempotencyKey, payloadHash,
                taskId, bindingId, "RESUME", payloadJson, now);
        log.info("恢复命令受理成功 taskId={} commandId={} userId={}", taskId, commandId, userId);
        return new CommandReceipt(commandId, taskId, "PENDING", null, null);
    }

    private CommandReceipt toReceipt(CommandRow command) {
        return new CommandReceipt(command.commandId(), command.taskId(), command.status(),
                command.nativeTurnId(), null);
    }

    private String requireMasterBinding(String userId, String taskId) {
        String bindingId = bindingMapper.findMasterBindingId(userId, taskId);
        if (bindingId == null) {
            throw new IllegalStateException("目标缺少 Master 绑定：" + taskId);
        }
        return bindingId;
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "幂等键长度必须为 1 到 160");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("控制请求无法序列化", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
