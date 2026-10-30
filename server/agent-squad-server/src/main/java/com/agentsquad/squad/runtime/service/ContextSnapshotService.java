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

    @Resource
    private ExecutionAuthorizationService authorization;

    @Resource
    private com.agentsquad.squad.memory.service.MemoryContextService memories;

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
        return prepare(userId, sessionId, new com.agentsquad.squad.runtime.dto.ContextPreparationRequest(
                targetNodeKey, logicalKey, List.of(), List.of()));
    }

    /**
     * 冻结必要的技能与记忆子集；重放需保持相同选择，不从用户材料之外接受模型伪造的来源。
     */
    @Transactional
    public ContextSnapshotReceipt prepare(String userId, String sessionId,
            com.agentsquad.squad.runtime.dto.ContextPreparationRequest request) {
        String targetNodeKey = request.targetNodeKey();
        String logicalKey = request.logicalKey();
        if (request.skillVersionIds().size() > 64 || request.memoryIds().size() > 20
                || request.skillVersionIds().stream().distinct().count() != request.skillVersionIds().size()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "局部技能或记忆选择超出范围");
        }
        String scopeId = executionScopes.scopeForSession(userId, sessionId);
        var fixed = authorization.prepare(userId, scopeId);
        var selectedSkills = fixed.skills().skills().stream()
                .filter(skill -> request.skillVersionIds().contains(skill.skillVersionId())).toList();
        if (selectedSkills.size() != request.skillVersionIds().size()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "局部技能不属于当前固定目录");
        }
        var selectedMemory = memories.select(userId, scopeId, request.memoryIds());
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
            var priorItems = readItems(prior.itemsJson());
            var priorSkills = new java.util.HashSet<String>();
            var priorMemories = new java.util.HashSet<String>();
            for (var item : priorItems) {
                if ("SKILL_REFERENCE".equals(item.path("kind").asText())) { priorSkills.add(item.path("sourceId").asText()); }
                if ("EXPLICIT_MEMORY".equals(item.path("kind").asText())) { priorMemories.add(item.path("sourceId").asText()); }
            }
            if (!priorSkills.equals(java.util.Set.copyOf(request.skillVersionIds()))
                    || !priorMemories.equals(java.util.Set.copyOf(request.memoryIds()))) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同上下文逻辑键的资料选择不一致");
            }
            return new ContextSnapshotReceipt(prior.snapshotId(), targetNodeKey, prior.contentHash());
        }

        String message = originalMessage(context.inputJson());
        var applied = directives.effectiveApplied(userId, context.taskId());
        var items = new java.util.ArrayList<com.agentsquad.squad.runtime.dto.ContextMaterial>();
        boolean replaced = applied.stream().anyMatch(item -> "REPLACE".equals(item.kind()));
        items.add(new com.agentsquad.squad.runtime.dto.ContextMaterial(
                replaced ? "USER_ORIGIN_REFERENCE" : "USER_CONSTRAINT", context.taskId(), "1", "USER", message));
        for (var directive : applied) {
            items.add(new com.agentsquad.squad.runtime.dto.ContextMaterial(
                    "REPLACE".equals(directive.kind()) ? "USER_GOAL_REPLACEMENT" : "USER_SUPPLEMENT",
                    directive.sourceMessageId(), String.valueOf(directive.appliedGoalRevision()), "USER", directive.sourceText()));
            message += "\n" + directive.sourceText();
        }
        for (var skill : selectedSkills) {
            items.add(new com.agentsquad.squad.runtime.dto.ContextMaterial("SKILL_REFERENCE", skill.skillVersionId(),
                    skill.version(), "UNTRUSTED", skill.description(), skill.contentHash(), skill.authorizationRevision()));
        }
        for (var memory : selectedMemory.memories()) {
            items.add(new com.agentsquad.squad.runtime.dto.ContextMaterial("EXPLICIT_MEMORY", memory.memoryId(),
                    String.valueOf(memory.version()), "USER_DATA", memory.content()));
        }
        String itemsJson = RuntimePayloads.toJson(objectMapper, items);
        String snapshotId = UUID.randomUUID().toString();
        String contentHash = CanonicalJson.sha256(objectMapper, itemsJson);
        long tokenEstimate = Math.max(1, (itemsJson.codePointCount(0, itemsJson.length()) + 3L) / 4L);
        snapshotMapper.insert(snapshotId, userId, context.taskId(), targetNodeKey, logicalKey,
                context.conversationGeneration(), itemsJson, tokenEstimate, contentHash, goalRevision, selectedMemory.memoryRevision());
        log.info("局部上下文已冻结 taskId={} nodeKey={} snapshotId={} tokenEstimate={}",
                context.taskId(), targetNodeKey, snapshotId, tokenEstimate);
        return new ContextSnapshotReceipt(snapshotId, targetNodeKey, contentHash);
    }

    private com.fasterxml.jackson.databind.JsonNode readItems(String value) {
        try {
            return RuntimePayloads.readJson(objectMapper, value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("固定上下文无法读取", exception);
        }
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
