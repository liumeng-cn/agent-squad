package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

import com.agentsquad.squad.conversation.domain.*;
import com.agentsquad.squad.conversation.dto.CommandReceipt;
import com.agentsquad.squad.conversation.repository.*;
import com.agentsquad.squad.runtime.repository.*;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * 用户目标控制命令受理服务。负责取消与恢复意图的幂等持久化，不直接操作框架运行。
 *
 * <p>控制命令与任务状态变更在同一事务中提交；Bridge 后续按命令投递并执行 SDK 侧合作停止或恢复。</p>
 */
@Service
@Slf4j
public class TaskControlService {

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private DeliveryMapper deliveryMapper;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

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
        String payloadJson = RuntimePayloads.toJson(objectMapper,
                Map.of("action", RuntimeCommandAction.STOP.name(), "expectedVersion", expectedVersion));
        String payloadHash = ContentDigests.sha256(payloadJson);
        CommandRow prior = commandMapper.findByKey(userId, scopeKey, idempotencyKey);
        if (prior != null) {
            if (!prior.payloadHash().equals(payloadHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return toReceipt(prior);
        }
        if (GoalTaskStatus.valueOf(task.status()).isTerminal()) {
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
                taskId, bindingId, RuntimeCommandAction.STOP.name(), payloadJson, now);
        log.info("取消命令受理成功 taskId={} commandId={} userId={}", taskId, commandId, userId);
        return new CommandReceipt(commandId, taskId, RuntimeCommandStatus.PENDING.name(), null, null);
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
        String payloadJson = RuntimePayloads.toJson(objectMapper,
                Map.of("action", RuntimeCommandAction.RESUME.name(), "expectedVersion", expectedVersion));
        String payloadHash = ContentDigests.sha256(payloadJson);
        CommandRow prior = commandMapper.findByKey(userId, scopeKey, idempotencyKey);
        if (prior != null) {
            if (!prior.payloadHash().equals(payloadHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return toReceipt(prior);
        }
        GoalTaskStatus status = GoalTaskStatus.valueOf(task.status());
        if (status.isTerminal()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已结束，不可恢复");
        }
        if (!status.isResumable()) {
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
                taskId, bindingId, RuntimeCommandAction.RESUME.name(), payloadJson, now);
        log.info("恢复命令受理成功 taskId={} commandId={} userId={}", taskId, commandId, userId);
        return new CommandReceipt(commandId, taskId, RuntimeCommandStatus.PENDING.name(), null, null);
    }

    /**
     * 原生恢复明确拒绝或多次失败时，命令回执与任务状态在一个事务中阻塞。
     * 若取消已抢先受理，只阻塞失效的恢复命令，不覆盖取消状态。
     */
    @Transactional
    public void blockRejectedResume(String userId, String taskId, String commandId,
                                    String deliveryToken, String errorCode) {
        if (deliveryMapper.blockRejectedResume(commandId, deliveryToken, errorCode) != 1) {
            return;
        }
        String reason = "原生会话拒绝恢复，请核对运行记录后重新处理";
        if (taskMapper.blockRejectedResume(userId, taskId, reason) == 1) {
            if (projectionMapper.reserveEventSequence(userId, taskId) != 1) {
                throw new IllegalStateException("恢复阻塞事件序号保留失败");
            }
            Long sequence = projectionMapper.eventSequence(userId, taskId);
            taskEventMapper.insertProjected(userId, taskId, sequence,
                    "resume:" + commandId + ":blocked", "task.recovery_blocked",
                    RuntimePayloads.toJson(objectMapper, Map.of("reason", reason, "errorCode", errorCode)),
                    LocalDateTime.now(ZoneOffset.UTC));
        }
        log.warn("原生恢复命令被阻塞 taskId={} commandId={} errorCode={}", taskId, commandId, errorCode);
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

}
