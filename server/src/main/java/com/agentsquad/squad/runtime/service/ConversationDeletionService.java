package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.domain.CommandRow;
import com.agentsquad.squad.runtime.domain.ConversationRow;
import com.agentsquad.squad.runtime.domain.TaskRow;
import com.agentsquad.squad.runtime.repository.BindingMapper;
import com.agentsquad.squad.runtime.repository.CommandMapper;
import com.agentsquad.squad.runtime.repository.ConversationMapper;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.repository.TaskEventMapper;
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
 * 会话删除受理服务。校验版本后把会话置为 DELETING，并就地收尾未受理目标；
 * 已运行的目标只写停止意图，等待原生回合结束后由投影终态。
 *
 * <p>删除意图本身作为命令持久化，重放同键同输入返回原受理；
 * 删除中的会话读取立即拒绝、列表隐藏、提交被屏障拦截。</p>
 */
@Service
@Slf4j
public class ConversationDeletionService {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED");

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private BindingMapper bindingMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 受理删除：锁会话行校验版本，置 DELETING 并处理在途目标。
     *
     * <p>QUEUED 目标尚未被原生会话受理，撤销未送达命令后直接平台终态；
     * 运行中目标写停止命令，由原生投影收尾。任何失败整体回滚。</p>
     */
    @Transactional
    public void delete(String userId, String conversationId, String idempotencyKey, long expectedVersion) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "幂等键长度必须为 1 到 160");
        }
        ConversationRow conversation = conversationMapper.lockOwned(userId, conversationId);
        if (conversation == null || "DELETED".equals(conversation.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "会话不存在");
        }
        String scopeKey = "conversation.delete:" + conversationId;
        String payloadJson = toJson(Map.of(
                "action", "DELETE_CONVERSATION", "expectedVersion", expectedVersion));
        String payloadHash = sha256(payloadJson);
        CommandRow prior = commandMapper.findByKey(userId, scopeKey, idempotencyKey);
        if (prior != null) {
            if (!prior.payloadHash().equals(payloadHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return;
        }
        if (!"ACTIVE".equals(conversation.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话正在删除");
        }
        if (conversation.version() != expectedVersion) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话版本已变化");
        }
        if (conversationMapper.markDeleting(userId, conversationId, expectedVersion) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话版本已变化");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String activeTaskId = conversation.activeTaskId();
        if (activeTaskId != null) {
            settleActiveTask(userId, conversationId, activeTaskId, now);
        }
        commandMapper.insertControl(UUID.randomUUID().toString(), userId, scopeKey,
                idempotencyKey, payloadHash, null, null, "DELETE_CONVERSATION", payloadJson, now);
        log.info("会话删除受理成功 conversationId={} userId={} activeTaskId={}",
                conversationId, userId, activeTaskId);
    }

    private void settleActiveTask(String userId, String conversationId, String taskId, LocalDateTime now) {
        TaskRow task = taskMapper.lockOwned(userId, taskId);
        if (task == null || TERMINAL_STATUSES.contains(task.status())) {
            conversationMapper.clearActiveTask(userId, conversationId, taskId);
            return;
        }
        commandMapper.cancelPendingByTask(userId, taskId);
        if ("QUEUED".equals(task.status())) {
            if (taskMapper.updateCancelled(userId, taskId, now) != 1) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标状态已变化");
            }
            conversationMapper.clearActiveTask(userId, conversationId, taskId);
            publishTaskCompleted(userId, taskId, "CANCELLED", now);
            log.info("未受理目标随删除就地取消 taskId={} conversationId={}", taskId, conversationId);
            return;
        }
        if (taskMapper.updateCancelling(userId, taskId, task.version(), now) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标状态已变化");
        }
        String stopPayload = toJson(Map.of("action", "STOP"));
        commandMapper.insertControl(UUID.randomUUID().toString(), userId, "task.cancel:" + taskId,
                "conversation-delete", sha256(stopPayload), taskId,
                bindingMapper.findMasterBindingId(userId, taskId), "STOP", stopPayload, now);
    }

    private void publishTaskCompleted(String userId, String taskId, String status, LocalDateTime now) {
        if (projectionMapper.reserveEventSequence(userId, taskId) != 1) {
            throw new IllegalStateException("事件序号保留失败");
        }
        Long sequence = projectionMapper.eventSequence(userId, taskId);
        if (sequence == null) {
            throw new IllegalStateException("事件序号读取失败");
        }
        taskEventMapper.insertProjected(userId, taskId, sequence,
                "conversation-delete:" + taskId + ":task.completed", "task.completed",
                toJson(Map.of("status", status)), now);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("删除请求无法序列化", exception);
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
