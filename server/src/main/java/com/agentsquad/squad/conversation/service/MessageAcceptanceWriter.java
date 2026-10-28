package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.artifact.ArtifactMapper;
import com.agentsquad.squad.artifact.ArtifactService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.agentsquad.squad.conversation.domain.MessageSubmissionRow;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户消息的原子受理事务。
 * <p>
 * 写入会话、消息、独立回复、绑定、命令及事件；不创建GoalTask或调用SDK，任何失败整体回滚。
 */
@Service
public class MessageAcceptanceWriter {

    @Resource
    private ConversationMapper conversations;

    @Resource
    private MessageAcceptanceMapper messages;

    @Resource
    private ExecutionScopeMapper scopes;

    @Resource
    private BindingMapper bindings;

    @Resource
    private CommandMapper commands;

    @Resource
    private ArtifactService artifacts;

    @Resource
    private ArtifactMapper artifactMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${agentsquad.budget.max-microunits-per-response:5000000}")
    private long responseBudgetLimit;

    /**
     * 独立受理边界保证并发唯一键失败可先回滚，再由外层补查胜出收据，避免在失败事务中继续读写。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MessageSubmissionRow accept(String userId, String conversationId, String scopeKey, String key,
                                       MessageCreateRequest request, String payloadHash) {
        ConversationRow conversation = conversationId == null ? null : lockConversation(userId, conversationId);
        MessageSubmissionRow prior = messages.findSubmission(userId, scopeKey, key);
        if (prior != null) {
            return prior;
        }
        if (conversation == null) {
            conversationId = UUID.randomUUID().toString();
            conversations.insert(conversationId, userId, fallbackTitle(request.text()));
            messages.markFallbackTitle(userId, conversationId);
            conversation = lockConversation(userId, conversationId);
        }
        String messageId = UUID.randomUUID().toString();
        String executionId = UUID.randomUUID().toString();
        String scopeId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();
        String bindingId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (responseBudgetLimit <= 0) {
            throw new IllegalStateException("回复预算上限必须大于0");
        }
        scopes.insertAccount(userId, accountId, "STANDARD", responseBudgetLimit);
        scopes.insertScope(scopeId, userId, conversationId, conversation.generation(), "RESPONSE", executionId, accountId);
        if (messages.reserveMessage(userId, conversationId, now) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话已停止接收消息");
        }
        long sequence = messages.messageSequence(userId, conversationId);
        messages.insertMessage(messageId, userId, conversationId, sequence, request.text(), executionId, now);
        bindings.insertResponse(bindingId, userId, scopeId, MasterAgentIdentity.AGENT_ID,
                UUID.randomUUID().toString(), now);
        messages.insertExecution(executionId, userId, conversationId, messageId, scopeId, bindingId);
        artifacts.attachScope(userId, scopeId, request.artifactIds());
        for (String artifactId : request.artifactIds()) {
            artifactMapper.attachMessage(userId, messageId, artifactId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", request.text());
        payload.put("messageId", messageId);
        payload.put("messageSequence", sequence);
        payload.put("executionId", executionId);
        payload.put("conversationId", conversationId);
        payload.put("artifactIds", request.artifactIds());
        commands.insertControl(commandId, userId, scopeKey, key, payloadHash, null, bindingId,
                "MESSAGE_SUBMIT", RuntimePayloads.toJson(objectMapper, payload), now);
        appendEvent(userId, conversation, commandId, "message.accepted",
                Map.of("messageId", messageId, "executionId", executionId, "sequence", sequence), now);
        appendEvent(userId, conversation, commandId, "execution.queued",
                Map.of("executionId", executionId, "status", "QUEUED"), now);
        return messages.findSubmission(userId, scopeKey, key);
    }

    private ConversationRow lockConversation(String userId, String conversationId) {
        ConversationRow row = conversations.lockOwned(userId, conversationId);
        if (row == null || !"ACTIVE".equals(row.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "对话不存在或已删除");
        }
        return row;
    }

    private void appendEvent(String userId, ConversationRow conversation, String commandId, String type,
                             Map<String, Object> payload, LocalDateTime now) {
        if (messages.reserveEvent(userId, conversation.conversationId()) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话公开水位已失效");
        }
        messages.insertEvent(userId, conversation.conversationId(), conversation.generation(),
                messages.eventSequence(userId, conversation.conversationId()), "command:" + commandId + ":" + type,
                type, RuntimePayloads.toJson(objectMapper, payload), now);
    }

    private String fallbackTitle(String text) {
        String title = text.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").replaceAll("(?U)\\s+", " ").strip();
        if (title.isEmpty()) {
            return "新对话";
        }
        int length = Math.min(32, title.codePointCount(0, title.length()));
        return title.substring(0, title.offsetByCodePoints(0, length));
    }
}
