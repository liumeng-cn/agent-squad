package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.conversation.repository.TaskDirectiveMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.GoalTaskStatus;
import com.agentsquad.squad.runtime.domain.ContextPreparationRow;
import com.agentsquad.squad.runtime.domain.ContextSnapshotRow;
import com.agentsquad.squad.runtime.dto.ContextSnapshotReceipt;
import com.agentsquad.squad.runtime.repository.ContextSnapshotMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Worker 局部上下文冻结服务。
 *
 * 仅从当前用户目标的原始输入生成受控快照，不接受模型自行标注的来源或整段会话；
 * 同一逻辑键重放返回同一快照，后续发布和投递仍须复核归属与对话代际。
 */
@Service
@Slf4j
public class ContextSnapshotService {

    private static final Pattern NODE_KEY = Pattern.compile("[a-z][a-z0-9_-]{0,79}");

    @Resource
    private TaskDirectiveMapper directives;

    @Resource
    private ExecutionScopeService executionScopes;

    @Resource
    private ContextSnapshotMapper snapshotMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 锁定受控 Master 目标并冻结原始用户约束；数据库提交后回执丢失可按逻辑键重放。
     */
    @Transactional
    public ContextSnapshotReceipt prepare(String userId, String sessionId,
                                          String targetNodeKey, String logicalKey) {
        if (targetNodeKey == null || !NODE_KEY.matcher(targetNodeKey).matches()
                || logicalKey == null || logicalKey.isBlank() || logicalKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "局部上下文键无效");
        }
        executionScopes.lockOwned(userId, executionScopes.scopeForSession(userId, sessionId), true);
        ContextPreparationRow context = snapshotMapper.lockPreparation(userId, sessionId);
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Master 会话未绑定活跃目标");
        }
        if (context.cancellationRequested() || !"ACTIVE".equals(context.conversationStatus())
                || !(GoalTaskStatus.QUEUED.name().equals(context.taskStatus())
                || GoalTaskStatus.RUNNING.name().equals(context.taskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已不能准备局部上下文");
        }

        long goalRevision = directives.goalState(userId, context.taskId()).goalRevision();
        ContextSnapshotRow prior = snapshotMapper.findByLogicalKey(userId, context.taskId(),
                targetNodeKey, logicalKey);
        if (prior != null) {
            if (prior.conversationGeneration() != context.conversationGeneration() || prior.goalRevision() != goalRevision) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "上下文所属对话代际已变化");
            }
            return new ContextSnapshotReceipt(prior.snapshotId(), targetNodeKey, prior.contentHash());
        }

        String message = originalMessage(context.inputJson());
        var applied = directives.effectiveApplied(userId, context.taskId());
        var items = new java.util.ArrayList<Map<String, String>>();
        boolean replaced = applied.stream().anyMatch(item -> "REPLACE".equals(item.kind()));
        items.add(Map.of(
                "kind", replaced ? "USER_ORIGIN_REFERENCE" : "USER_CONSTRAINT",
                "sourceId", context.taskId(),
                "sourceVersion", "1",
                "trust", "USER",
                "content", message));
        for (var directive : applied) {
            items.add(Map.of("kind", "REPLACE".equals(directive.kind()) ? "USER_GOAL_REPLACEMENT" : "USER_SUPPLEMENT",
                    "sourceId", directive.sourceMessageId(), "sourceVersion", String.valueOf(directive.appliedGoalRevision()),
                    "trust", "USER", "content", directive.sourceText()));
            message += "\n" + directive.sourceText();
        }
        String itemsJson = RuntimePayloads.toJson(objectMapper, items);
        String snapshotId = UUID.randomUUID().toString();
        String contentHash = CanonicalJson.sha256(objectMapper, itemsJson);
        long tokenEstimate = Math.max(1, (message.codePointCount(0, message.length()) + 3L) / 4L);
        snapshotMapper.insert(snapshotId, userId, context.taskId(), targetNodeKey, logicalKey,
                context.conversationGeneration(), itemsJson, tokenEstimate, contentHash, goalRevision);
        log.info("局部上下文已冻结 taskId={} nodeKey={} snapshotId={} tokenEstimate={}",
                context.taskId(), targetNodeKey, snapshotId, tokenEstimate);
        return new ContextSnapshotReceipt(snapshotId, targetNodeKey, contentHash);
    }

    private String originalMessage(String inputJson) {
        try {
            String message = RuntimePayloads.readJson(objectMapper, inputJson).path("message").asText();
            if (message.isBlank()) {
                throw new IllegalStateException("目标原始输入缺少 message");
            }
            return message;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("目标原始输入无法解析", exception);
        }
    }
}
