package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.CanonicalJson;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.bo.ContextMaterialBO;
import com.agentsquad.squad.bo.ContextPreparationRequestBO;
import com.agentsquad.squad.enums.GoalTaskStatus;
import com.agentsquad.squad.bo.ContextPreparationBO;
import com.agentsquad.squad.po.ContextSnapshotPO;
import com.agentsquad.squad.bo.ContextSnapshotReceiptBO;
import com.agentsquad.squad.mapper.ContextSnapshotMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private com.agentsquad.squad.service.MemoryContextService memories;

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
    public ContextSnapshotReceiptBO prepare(String userId, String sessionId,
                                          String targetNodeKey, String logicalKey) {
        return prepare(userId, sessionId, new ContextPreparationRequestBO(
                targetNodeKey, logicalKey, List.of(), List.of()));
    }

    /**
     * 冻结必要的技能与记忆子集；重放需保持相同选择，不从用户材料之外接受模型伪造的来源。
     */
    @Transactional
    public ContextSnapshotReceiptBO prepare(String userId, String sessionId,
            ContextPreparationRequestBO request) {
        String targetNodeKey = request.targetNodeKey();
        String logicalKey = request.logicalKey();
        if (request.skillVersionIds().size() > 64 || request.memoryIds().size() > 20
                || request.skillVersionIds().stream().distinct().count() != request.skillVersionIds().size()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "局部技能或记忆选择超出范围");
        }
        String scopeId = executionScopes.scopeForSession(userId, sessionId);
        var fixed = authorization.prepare(userId, scopeId);
        var selectedSkills = fixed.skills().skills().stream()
                .filter(skill -> request.skillVersionIds().contains(skill.getSkillVersionId())).toList();
        if (selectedSkills.size() != request.skillVersionIds().size()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "局部技能不属于当前固定目录");
        }
        var selectedMemory = memories.select(userId, scopeId, request.memoryIds());
        if (targetNodeKey == null || !NODE_KEY.matcher(targetNodeKey).matches()
                || logicalKey == null || logicalKey.isBlank() || logicalKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "局部上下文键无效");
        }
        executionScopes.lockOwned(userId, executionScopes.scopeForSession(userId, sessionId), true);
        ContextPreparationBO context = snapshotMapper.lockPreparation(userId, sessionId);
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Master 会话未绑定活跃目标");
        }
        if (context.isCancellationRequested() || !"ACTIVE".equals(context.getConversationStatus())
                || !(GoalTaskStatus.QUEUED.name().equals(context.getTaskStatus())
                || GoalTaskStatus.RUNNING.name().equals(context.getTaskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已不能准备局部上下文");
        }

        long goalRevision = directives.goalState(userId, context.getTaskId()).getGoalRevision();
        ContextSnapshotPO prior = snapshotMapper.findByLogicalKey(userId, context.getTaskId(),
                targetNodeKey, logicalKey);
        if (prior != null) {
            if (prior.getConversationGeneration() != context.getConversationGeneration() || prior.getGoalRevision() != goalRevision) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "上下文所属对话代际已变化");
            }
            var priorItems = readItems(prior.getItemsJson());
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
            return new ContextSnapshotReceiptBO(prior.getSnapshotId(), targetNodeKey, prior.getContentHash());
        }

        String message = originalMessage(context.getInputJson());
        var applied = directives.effectiveApplied(userId, context.getTaskId());
        var items = new java.util.ArrayList<ContextMaterialBO>();
        boolean replaced = applied.stream().anyMatch(item -> "REPLACE".equals(item.getKind()));
        items.add(new ContextMaterialBO(
                replaced ? "USER_ORIGIN_REFERENCE" : "USER_CONSTRAINT", context.getTaskId(), "1", "USER", message));
        for (var directive : applied) {
            items.add(new ContextMaterialBO(
                    "REPLACE".equals(directive.getKind()) ? "USER_GOAL_REPLACEMENT" : "USER_SUPPLEMENT",
                    directive.getSourceMessageId(), String.valueOf(directive.getAppliedGoalRevision()), "USER", directive.getSourceText()));
            message += "\n" + directive.getSourceText();
        }
        for (var skill : selectedSkills) {
            items.add(new ContextMaterialBO("SKILL_REFERENCE", skill.getSkillVersionId(),
                    skill.getVersion(), "UNTRUSTED", skill.getDescription(), skill.getContentHash(), skill.getAuthorizationRevision()));
        }
        for (var memory : selectedMemory.memories()) {
            items.add(new ContextMaterialBO("EXPLICIT_MEMORY", memory.memoryId(),
                    String.valueOf(memory.version()), "USER_DATA", memory.content()));
        }
        String itemsJson = RuntimePayloads.toJson(objectMapper, items);
        String snapshotId = UUID.randomUUID().toString();
        String contentHash = CanonicalJson.sha256(objectMapper, itemsJson);
        long tokenEstimate = Math.max(1, (itemsJson.codePointCount(0, itemsJson.length()) + 3L) / 4L);
        snapshotMapper.insert(snapshotId, userId, context.getTaskId(), targetNodeKey, logicalKey,
                context.getConversationGeneration(), itemsJson, tokenEstimate, contentHash, goalRevision, selectedMemory.memoryRevision());
        log.info("局部上下文已冻结 taskId={} nodeKey={} snapshotId={} tokenEstimate={}",
                context.getTaskId(), targetNodeKey, snapshotId, tokenEstimate);
        return new ContextSnapshotReceiptBO(snapshotId, targetNodeKey, contentHash);
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
