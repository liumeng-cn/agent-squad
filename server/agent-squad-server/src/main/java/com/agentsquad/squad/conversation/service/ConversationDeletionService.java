package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.presentation.service.TaskEventPublishingService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

import com.agentsquad.squad.conversation.domain.CommandRow;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.domain.ConversationStatus;
import com.agentsquad.squad.conversation.domain.GoalTaskStatus;
import com.agentsquad.squad.conversation.domain.RuntimeCommandAction;
import com.agentsquad.squad.conversation.domain.TaskRow;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
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
 * 会话删除受理服务。校验版本后把会话置为 DELETING，并就地收尾未受理目标；
 * 已运行的目标只写停止意图，等待原生回合结束后由投影终态。
 *
 * <p>删除意图本身作为命令持久化，重放同键同输入返回原受理；
 * 删除中的会话读取立即拒绝、列表隐藏、提交被屏障拦截。</p>
 */
@Service
@Slf4j
public class ConversationDeletionService {

    @Resource
    private TaskEventPublishingService conversationProjection;

    @Resource
    private TaskControlService taskControls;

    @Resource
    private com.agentsquad.squad.runtime.repository.CompletionMapper completion;

    @Resource
    private ResponseControlService responseControls;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private TaskMapper taskMapper;

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
     * <p>只有没有投递尝试、计划和未决操作的排队目标才能直接结束；
     * 运行中目标写停止命令，由原生投影收尾。任何失败整体回滚。</p>
     */
    @Transactional
    public void delete(String userId, String conversationId, String idempotencyKey, long expectedVersion) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "幂等键长度必须为 1 到 160");
        }
        ConversationRow conversation = conversationMapper.lockOwned(userId, conversationId);
        if (conversation == null || ConversationStatus.DELETED.name().equals(conversation.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "会话不存在");
        }
        String scopeKey = "conversation.delete:" + conversationId;
        String payloadJson = RuntimePayloads.toJson(objectMapper, Map.of(
                "action", RuntimeCommandAction.DELETE_CONVERSATION.name(), "expectedVersion", expectedVersion));
        String payloadHash = ContentDigests.sha256(payloadJson);
        CommandRow prior = commandMapper.findByKey(userId, scopeKey, idempotencyKey);
        if (prior != null) {
            if (!prior.payloadHash().equals(payloadHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return;
        }
        if (!ConversationStatus.ACTIVE.name().equals(conversation.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话正在删除");
        }
        if (conversation.version() != expectedVersion) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话版本已变化");
        }
        if (conversationMapper.markDeleting(userId, conversationId, expectedVersion) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话版本已变化");
        }
        responseControls.cancelForDeletion(userId, conversationId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String activeTaskId = conversation.activeTaskId();
        if (activeTaskId != null) {
            settleActiveTask(userId, conversationId, activeTaskId, now);
        }
        commandMapper.insertControl(UUID.randomUUID().toString(), userId, scopeKey,
                idempotencyKey, payloadHash, null, null,
                RuntimeCommandAction.DELETE_CONVERSATION.name(), payloadJson, now);
        log.info("会话删除受理成功 conversationId={} userId={} activeTaskId={}",
                conversationId, userId, activeTaskId);
    }

    private void settleActiveTask(String userId, String conversationId, String taskId, LocalDateTime now) {
        TaskRow task = taskMapper.lockOwned(userId, taskId);
        if (task == null || GoalTaskStatus.valueOf(task.status()).isTerminal()) {
            conversationMapper.clearActiveTask(userId, conversationId, taskId);
            return;
        }
        if (task.cancelRequestedAt() != null) {
            // 已受理的停止命令继续执行，删除不能撤销这些命令再重新创建一组。
            return;
        }
        commandMapper.cancelPendingByTask(userId, taskId);
        if (GoalTaskStatus.QUEUED.name().equals(task.status()) && task.planVersion() == 0
                && taskMapper.countStartedCommands(userId, taskId) == 0
                && completion.countPendingEffects(userId, taskId) == 0) {
            if (taskMapper.updateCancelled(userId, taskId, now) != 1) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标状态已变化");
            }
            conversationMapper.clearActiveTask(userId, conversationId, taskId);
            publishTaskCompleted(userId, taskId, GoalTaskStatus.CANCELLED.name(), now);
            log.info("未受理目标随删除就地取消 taskId={} conversationId={}", taskId, conversationId);
            return;
        }
        taskControls.cancel(userId, taskId, "conversation-delete", task.version());
    }

    private void publishTaskCompleted(String userId, String taskId, String status, LocalDateTime now) {
        if (projectionMapper.reserveEventSequence(userId, taskId) != 1) {
            throw new IllegalStateException("事件序号保留失败");
        }
        Long sequence = projectionMapper.eventSequence(userId, taskId);
        if (sequence == null) {
            throw new IllegalStateException("事件序号读取失败");
        }
        conversationProjection.insertProjected(userId, taskId, sequence,
                "conversation-delete:" + taskId + ":task.completed", "task.completed",
                RuntimePayloads.toJson(objectMapper, Map.of("status", status)), now);
    }
}
