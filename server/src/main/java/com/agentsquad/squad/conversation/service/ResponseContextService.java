package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.artifact.ArtifactMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.domain.TaskRow;
import com.agentsquad.squad.conversation.domain.ResponseTaskFacts;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.conversation.repository.ResponseTaskFactsMapper;
import com.agentsquad.squad.conversation.domain.ResponseContextMessage;
import com.agentsquad.squad.conversation.domain.ResponseContextRow;
import com.agentsquad.squad.conversation.domain.ResponseHistoryRow;
import com.agentsquad.squad.conversation.domain.ResponseContextSnapshot;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.repository.ResponseContextMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 普通回复的会话前缀冻结服务。
 * <p>
 * 按会话和实际所有者串行化首次材料选择；重投复用原内容，读取前仍验证代际与附件权限。
 */
@Service
public class ResponseContextService {

    private static final int MAX_CONTEXT_CHARACTERS = 200000;

    @Resource
    private ExecutionScopeService scopeService;

    @Resource
    private ExecutionScopeMapper scopeMapper;

    @Resource
    private ResponseContextMapper mapper;

    @Resource
    private ArtifactMapper artifacts;

    @Resource
    private ConversationMapper conversations;

    @Resource
    private TaskMapper tasks;

    @Resource
    private ResponseTaskFactsMapper taskFacts;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * SDK 提交前独立调用并提交快照；事务不接触模型，失败不会留下半份上下文。
     * 超限明确拒绝，不截断用户前文；预算内摘要能力由后续治理接入。
     */
    @Transactional
    public ResponseContextSnapshot freeze(String userId, String bindingId) {
        ExecutionScopeRow scope = scopeService.lockOwned(userId,
                scopeMapper.findScopeByBinding(userId, bindingId), true);
        if (!"RESPONSE".equals(scope.kind())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "只有回复执行可读取会话前缀");
        }
        ResponseContextRow source = mapper.findSource(userId, bindingId);
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
        List<ResponseContextMessage> messages = new ArrayList<>();
        long characters = 0;
        for (ResponseHistoryRow row : mapper.prefix(userId, scope.conversationId(), source.sourceSequence())) {
            characters += row.text().length();
            if (characters > MAX_CONTEXT_CHARACTERS) {
                throw contextTooLarge();
            }
            messages.add(new ResponseContextMessage(row.messageId(), row.sequence(), row.role(), row.text(),
                    row.revision(), List.copyOf(mapper.artifactIds(userId, row.messageId()))));
        }
        if (messages.isEmpty() || !source.sourceMessageId().equals(messages.getLast().messageId())) {
            throw new IllegalStateException("回复上下文缺少来源消息");
        }
        validateArtifacts(userId, messages);
        ResponseContextSnapshot snapshot = new ResponseContextSnapshot(scope.conversationId(),
                scope.conversationGeneration(), source.sourceMessageId(), source.sourceSequence(), List.copyOf(messages), activeTask(scope));
        String json = RuntimePayloads.toJson(objectMapper, snapshot);
        if (mapper.freeze(userId, source.executionId(), json, ContentDigests.sha256(json)) != 1) {
            throw new IllegalStateException("回复上下文冻结发生竞争");
        }
        return snapshot;
    }

    /**
     * 当前目标与节点按会话→目标→Job顺序读取，与消息材料同事务冻结，不跨模型请求持锁。
     */
    private ResponseTaskFacts activeTask(ExecutionScopeRow scope) {
        ConversationRow conversation = conversations.lockOwned(scope.userId(), scope.conversationId());
        if (conversation.activeTaskId() == null) {
            return null;
        }
        TaskRow task = tasks.lockOwnedRow(scope.userId(), conversation.activeTaskId());
        if (task == null || !scope.conversationId().equals(task.conversationId())) {
            throw new IllegalStateException("会话当前目标归属不一致");
        }
        Long taskGeneration = taskFacts.scopeGeneration(scope.userId(), task.taskId());
        if (taskGeneration == null || taskGeneration != scope.conversationGeneration()) {
            return null;
        }
        return new ResponseTaskFacts(task.taskId(), task.status(), task.version(), task.planVersion(),
                task.cancelRequestedAt() != null, List.copyOf(taskFacts.jobs(scope.userId(), task.taskId(), task.planVersion())));
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
