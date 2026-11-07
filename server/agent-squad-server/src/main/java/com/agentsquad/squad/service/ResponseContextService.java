package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.mapper.ArtifactMapper;
import com.agentsquad.squad.domain.entity.ArtifactDO;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.MaterialFormatter;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.entity.ExecutionScopeDO;
import com.agentsquad.squad.domain.entity.ConversationDO;
import com.agentsquad.squad.domain.entity.TaskDO;
import com.agentsquad.squad.domain.model.ActivityBusinessFact;
import com.agentsquad.squad.domain.model.ResponseTaskFacts;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.mapper.ResponseTaskFactsMapper;
import com.agentsquad.squad.domain.model.ResponseContextMessage;
import com.agentsquad.squad.domain.model.ResponseContextMaterial;
import com.agentsquad.squad.domain.entity.ResponseContextDO;
import com.agentsquad.squad.domain.entity.ResponseHistoryDO;
import com.agentsquad.squad.domain.model.ResponseContextSnapshot;
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
    public ResponseContextSnapshot freeze(String userId, String bindingId) {
        ExecutionScopeDO scope = scopeService.lockOwned(userId,
                scopeMapper.findScopeByBinding(userId, bindingId), true);
        if (!"RESPONSE".equals(scope.kind())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "只有回复执行可读取会话前缀");
        }
        ResponseContextDO source = mapper.findSource(userId, bindingId);
        if (source == null || !scope.ownerId().equals(source.executionId())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "回复来源消息不存在");
        }
        if (source.contextJson() != null) {
            if (!ContentDigests.sha256(source.contextJson()).equals(source.contextHash())) {
                throw new IllegalStateException("回复上下文摘要不一致");
            }
            ResponseContextSnapshot snapshot = read(source.contextJson());
            validateArtifacts(userId, snapshot.messages());
            return snapshot;
        }

        if (mapper.characterCount(userId, scope.conversationId(), source.sourceSequence()) > MAX_CONTEXT_CHARACTERS) {
            throw contextTooLarge();
        }
        var excluded = memories.excludedHistoryMessageIds(userId);
        List<ResponseContextMessage> messages = new ArrayList<>();
        long characters = 0;
        for (ResponseHistoryDO row : mapper.prefix(userId, scope.conversationId(), source.sourceSequence())) {
            String text = excluded.contains(row.messageId()) && !source.sourceMessageId().equals(row.messageId())
                    ? "[已删除或纠正的记忆关联内容不再用于上下文]" : row.text();
            characters += text.length();
            if (characters > MAX_CONTEXT_CHARACTERS) {
                throw contextTooLarge();
            }
            List<String> artifactIds = mapper.artifactIds(userId, row.messageId());
            List<ResponseContextMaterial> materials = buildMaterials(userId, artifactIds);
            for (ResponseContextMaterial material : materials) {
                characters += material.content().length();
                if (characters > MAX_CONTEXT_CHARACTERS) {
                    throw contextTooLarge();
                }
            }
            messages.add(new ResponseContextMessage(row.messageId(), row.sequence(), row.role(), text,
                    row.revision(), List.copyOf(artifactIds), List.copyOf(materials)));
        }
        if (messages.isEmpty() || !source.sourceMessageId().equals(messages.getLast().messageId())) {
            throw new IllegalStateException("回复上下文缺少来源消息");
        }
        validateArtifacts(userId, messages);
        ResponseContextSnapshot snapshot = new ResponseContextSnapshot(scope.conversationId(),
                scope.conversationGeneration(), source.sourceMessageId(), source.sourceSequence(), List.copyOf(messages), activeTask(scope),
                List.copyOf(taskFacts.recentTasks(userId, scope.conversationId(), scope.conversationGeneration(), source.sourceSequence())),
                List.copyOf(taskFacts.recentFailures(userId, scope.conversationId(), scope.conversationGeneration(), source.sourceSequence())));
        String json = RuntimePayloads.toJson(objectMapper, snapshot);
        if (mapper.freeze(userId, source.executionId(), json, ContentDigests.sha256(json)) != 1) {
            throw new IllegalStateException("回复上下文冻结发生竞争");
        }
        activities.completed(scope, new com.agentsquad.squad.domain.model.ActivityBusinessFact(
                com.agentsquad.squad.common.enums.execution.ActivityType.CONTEXT_PREPARE, source.executionId()));
        log.info("回复上下文已冻结 conversationId={} messageCount={} materialCount={} contextChars={}",
                scope.conversationId(), messages.size(),
                messages.stream().mapToInt(m -> m.materials().size()).sum(), characters);
        return snapshot;
    }

    /**
     * 读取附件正文并按单文件预算格式化截断；归属与摘要校验由 ArtifactService 完成。
     */
    private List<ResponseContextMaterial> buildMaterials(String userId, List<String> artifactIds) {
        List<ResponseContextMaterial> materials = new ArrayList<>();
        for (String artifactId : artifactIds) {
            ArtifactDO artifact = artifactService.content(userId, artifactId);
            String raw = new String(artifact.content(), StandardCharsets.UTF_8);
            String formatted = MaterialFormatter.format(raw, artifact.mediaType(), MAX_MATERIAL_CHARACTERS);
            boolean truncated = formatted.length() < raw.length();
            materials.add(new ResponseContextMaterial(artifact.artifactId(), artifact.fileName(),
                    artifact.mediaType(), artifact.sizeBytes(), artifact.sha256(), formatted, truncated));
        }
        return materials;
    }

    /**
     * 当前目标与节点按会话→目标→Job顺序读取，与消息材料同事务冻结，不跨模型请求持锁。
     */
    private ResponseTaskFacts activeTask(ExecutionScopeDO scope) {
        ConversationDO conversation = conversations.lockOwned(scope.userId(), scope.conversationId());
        if (conversation.activeTaskId() == null) {
            return null;
        }
        TaskDO task = tasks.lockOwnedRow(scope.userId(), conversation.activeTaskId());
        if (task == null || !scope.conversationId().equals(task.conversationId())) {
            throw new IllegalStateException("会话当前目标归属不一致");
        }
        Long taskGeneration = taskFacts.scopeGeneration(scope.userId(), task.taskId());
        if (taskGeneration == null || taskGeneration != scope.conversationGeneration()) {
            return null;
        }
        return new ResponseTaskFacts(task.taskId(), task.status(), task.version(), task.planVersion(),
                task.cancelRequestedAt() != null, List.copyOf(taskFacts.jobs(scope.userId(), task.taskId(), task.planVersion())),
                directives.goalState(scope.userId(), task.taskId()).goalRevision(),
                List.copyOf(directives.latestFacts(scope.userId(), task.taskId())));
    }

    private RuntimeBusinessException contextTooLarge() {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                "对话材料超过当前可处理范围，请新建对话并提供必要材料");
    }

    private void validateArtifacts(String userId, List<ResponseContextMessage> messages) {
        for (ResponseContextMessage message : messages) {
            for (String id : message.artifactIds()) {
                if (artifacts.findOwned(userId, id) == null) {
                    throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "对话材料已失效或无权读取");
                }
            }
        }
    }

    private ResponseContextSnapshot read(String json) {
        try {
            return objectMapper.readValue(json, ResponseContextSnapshot.class);
        } catch (Exception exception) {
            throw new IllegalStateException("回复上下文无法读取", exception);
        }
    }
}
