package com.agentsquad.squad.service;

import com.agentsquad.squad.enums.ActivityType;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.mapper.ArtifactMapper;
import com.agentsquad.squad.model.bo.ActivityBusinessFactBO;
import com.agentsquad.squad.model.po.ArtifactPO;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.MaterialFormatter;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.po.ExecutionScopePO;
import com.agentsquad.squad.model.po.ConversationPO;
import com.agentsquad.squad.model.po.TaskPO;
import com.agentsquad.squad.model.bo.ResponseTaskFactsBO;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.mapper.ResponseTaskFactsMapper;
import com.agentsquad.squad.model.bo.ResponseContextMessageBO;
import com.agentsquad.squad.model.bo.ResponseContextMaterialBO;
import com.agentsquad.squad.model.bo.ResponseContextBO;
import com.agentsquad.squad.model.bo.ResponseHistoryBO;
import com.agentsquad.squad.model.bo.ResponseContextSnapshotBO;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.mapper.ResponseContextMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 普通回复的会话前缀冻结服务。
 * <p>
 * 按会话和实际所有者串行化首次材料选择；重投复用原内容，读取前仍验证代际与附件权限。
 * 附件正文随冻结快照固定，按预算截断并保留审计标记。
 */
@Service
@Slf4j
public class ResponseContextService {

    @Resource
    private com.agentsquad.squad.service.MemoryContextService memories;

    private static final int MAX_CONTEXT_CHARACTERS = 200000;

    private static final int MAX_MATERIAL_CHARACTERS = 20000;

    @Resource
    private TaskDirectiveMapper directives;

    @Resource
    private ExecutionScopeService scopeService;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ResponseContextMapper mapper;

    @Resource
    private ArtifactMapper artifacts;

    @Resource
    private ArtifactService artifactService;

    @Resource
    private ConversationMapper conversations;

    @Resource
    private TaskMapper tasks;

    @Resource
    private ResponseTaskFactsMapper taskFacts;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private com.agentsquad.squad.service.ActivityProjectionService activities;

    /**
     * SDK 提交前独立调用并提交快照；事务不接触模型，失败不会留下半份上下文。
     * 超限明确拒绝，不截断用户前文；附件正文按单文件预算截断并保留标记。
     */
    @Transactional
    public ResponseContextSnapshotBO freeze(String userId, String bindingId) {
        ExecutionScopePO scope = scopeService.lockOwned(userId,
                scopeMapper.findScopeByBinding(userId, bindingId), true);
        if (!"RESPONSE".equals(scope.getKind())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "只有回复执行可读取会话前缀");
        }
        ResponseContextBO source = mapper.findSource(userId, bindingId);
        if (source == null || !scope.getOwnerId().equals(source.getExecutionId())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "回复来源消息不存在");
        }
        if (source.getContextJson() != null) {
            if (!ContentDigests.sha256(source.getContextJson()).equals(source.getContextHash())) {
                throw new IllegalStateException("回复上下文摘要不一致");
            }
            ResponseContextSnapshotBO snapshot = read(source.getContextJson());
            validateArtifacts(userId, snapshot.messages());
            return snapshot;
        }

        if (mapper.characterCount(userId, scope.getConversationId(), source.getSourceSequence()) > MAX_CONTEXT_CHARACTERS) {
            throw contextTooLarge();
        }
        var excluded = memories.excludedHistoryMessageIds(userId);
        List<ResponseContextMessageBO> messages = new ArrayList<>();
        long characters = 0;
        for (ResponseHistoryBO row : mapper.prefix(userId, scope.getConversationId(), source.getSourceSequence())) {
            String text = excluded.contains(row.getMessageId()) && !source.getSourceMessageId().equals(row.getMessageId())
                    ? "[已删除或纠正的记忆关联内容不再用于上下文]" : row.getText();
            characters += text.length();
            if (characters > MAX_CONTEXT_CHARACTERS) {
                throw contextTooLarge();
            }
            List<String> artifactIds = mapper.artifactIds(userId, row.getMessageId());
            List<ResponseContextMaterialBO> materials = buildMaterials(userId, artifactIds);
            for (ResponseContextMaterialBO material : materials) {
                characters += material.content().length();
                if (characters > MAX_CONTEXT_CHARACTERS) {
                    throw contextTooLarge();
                }
            }
            messages.add(new ResponseContextMessageBO(row.getMessageId(), row.getSequence(), row.getRole(), text,
                    row.getRevision(), List.copyOf(artifactIds), List.copyOf(materials)));
        }
        if (messages.isEmpty() || !source.getSourceMessageId().equals(messages.getLast().messageId())) {
            throw new IllegalStateException("回复上下文缺少来源消息");
        }
        validateArtifacts(userId, messages);
        ResponseContextSnapshotBO snapshot = new ResponseContextSnapshotBO(scope.getConversationId(),
                scope.getConversationGeneration(), source.getSourceMessageId(), source.getSourceSequence(), List.copyOf(messages), activeTask(scope),
                List.copyOf(taskFacts.recentTasks(userId, scope.getConversationId(), scope.getConversationGeneration(), source.getSourceSequence())),
                List.copyOf(taskFacts.recentFailures(userId, scope.getConversationId(), scope.getConversationGeneration(), source.getSourceSequence())));
        String json = RuntimePayloads.toJson(objectMapper, snapshot);
        if (mapper.freeze(userId, source.getExecutionId(), json, ContentDigests.sha256(json)) != 1) {
            throw new IllegalStateException("回复上下文冻结发生竞争");
        }
        activities.completed(scope, new ActivityBusinessFactBO(
                ActivityType.CONTEXT_PREPARE, source.getExecutionId()));
        log.info("回复上下文已冻结 conversationId={} messageCount={} materialCount={} contextChars={}",
                scope.getConversationId(), messages.size(),
                messages.stream().mapToInt(m -> m.materials().size()).sum(), characters);
        return snapshot;
    }

    /**
     * 读取附件正文并按单文件预算格式化截断；归属与摘要校验由 ArtifactService 完成。
     */
    private List<ResponseContextMaterialBO> buildMaterials(String userId, List<String> artifactIds) {
        List<ResponseContextMaterialBO> materials = new ArrayList<>();
        for (String artifactId : artifactIds) {
            ArtifactPO artifact = artifactService.content(userId, artifactId);
            String raw = new String(artifact.getContent(), StandardCharsets.UTF_8);
            String formatted = MaterialFormatter.format(raw, artifact.getMediaType(), MAX_MATERIAL_CHARACTERS);
            boolean truncated = formatted.length() < raw.length();
            materials.add(new ResponseContextMaterialBO(artifact.getArtifactId(), artifact.getFileName(),
                    artifact.getMediaType(), artifact.getSizeBytes(), artifact.getSha256(), formatted, truncated));
        }
        return materials;
    }

    /**
     * 当前目标与节点按会话→目标→Job顺序读取，与消息材料同事务冻结，不跨模型请求持锁。
     */
    private ResponseTaskFactsBO activeTask(ExecutionScopePO scope) {
        ConversationPO conversation = conversations.lockOwned(scope.getUserId(), scope.getConversationId());
        if (conversation.getActiveTaskId() == null) {
            return null;
        }
        TaskPO task = tasks.lockOwnedRow(scope.getUserId(), conversation.getActiveTaskId());
        if (task == null || !scope.getConversationId().equals(task.getConversationId())) {
            throw new IllegalStateException("会话当前目标归属不一致");
        }
        Long taskGeneration = taskFacts.scopeGeneration(scope.getUserId(), task.getTaskId());
        if (taskGeneration == null || taskGeneration != scope.getConversationGeneration()) {
            return null;
        }
        return new ResponseTaskFactsBO(task.getTaskId(), task.getStatus(), task.getVersion(), task.getPlanVersion(),
                task.getCancelRequestedAt() != null, List.copyOf(taskFacts.jobs(scope.getUserId(), task.getTaskId(), task.getPlanVersion())),
                directives.goalState(scope.getUserId(), task.getTaskId()).getGoalRevision(),
                List.copyOf(directives.latestFacts(scope.getUserId(), task.getTaskId())));
    }

    private RuntimeBusinessException contextTooLarge() {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                "对话材料超过当前可处理范围，请新建对话并提供必要材料");
    }

    private void validateArtifacts(String userId, List<ResponseContextMessageBO> messages) {
        for (ResponseContextMessageBO message : messages) {
            for (String id : message.artifactIds()) {
                if (artifacts.findOwned(userId, id) == null) {
                    throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "对话材料已失效或无权读取");
                }
            }
        }
    }

    private ResponseContextSnapshotBO read(String json) {
        try {
            return objectMapper.readValue(json, ResponseContextSnapshotBO.class);
        } catch (Exception exception) {
            throw new IllegalStateException("回复上下文无法读取", exception);
        }
    }
}
