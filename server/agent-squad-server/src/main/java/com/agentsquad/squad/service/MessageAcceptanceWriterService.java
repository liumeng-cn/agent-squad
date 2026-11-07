package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.ArtifactMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.observability.ObservationSupport;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.entity.ConversationDO;
import com.agentsquad.squad.domain.entity.MessageSubmissionDO;
import com.agentsquad.squad.domain.model.ConversationCommittedSignal;
import com.agentsquad.squad.domain.request.MessageCreateRequest;
import com.agentsquad.squad.mapper.BindingMapper;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.mapper.MessageAcceptanceMapper;
import com.agentsquad.squad.domain.model.MasterAgentIdentity;
import com.agentsquad.squad.domain.enums.ResponseExecutionStatus;
import com.agentsquad.squad.domain.dto.MessageView;
import com.agentsquad.squad.domain.dto.ResponseExecutionView;
import com.agentsquad.squad.domain.dto.MessageAcceptedPayload;
import com.agentsquad.squad.domain.dto.ExecutionChangedPayload;
import com.agentsquad.squad.presentation.domain.ConversationDeltaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class MessageAcceptanceWriterService {
    @Resource
    private com.agentsquad.squad.common.observability.ExecutionTrace executionTrace;

    @org.springframework.beans.factory.annotation.Value("${agentsquad.response.completion-protocol:NATURAL}")
    private com.agentsquad.squad.domain.enums.ResponseCompletionProtocol completionProtocol;

    @Resource
    private com.agentsquad.squad.service.ModelCatalogService modelCatalog;

    @Resource
    private ApplicationEventPublisher committedEvents;

    @Resource
    private ObservationSupport observations;

    @Resource
    private FinalDeliveryService finalDeliveries;

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
     * 独立受理事务保证并发唯一键失败可先回滚，再由外层补查胜出收据，避免在失败事务中继续读写
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MessageSubmissionDO accept(String userId, String conversationId, String scopeKey, String key,
                                       MessageCreateRequest request, String payloadHash) {
        ConversationDO conversation = conversationId == null ? null : lockConversation(userId, conversationId);
        MessageSubmissionDO prior = messages.findSubmission(userId, scopeKey, key);
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
        executionTrace.capture(userId, scopeId);
        if (messages.reserveMessage(userId, conversationId, now) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话已停止接收消息");
        }
        long sequence = messages.messageSequence(userId, conversationId);
        messages.insertMessage(messageId, userId, conversationId, sequence, request.text(), executionId, now);
        bindings.insertResponse(bindingId, userId, scopeId, MasterAgentIdentity.AGENT_ID,
                UUID.randomUUID().toString(), now);
        messages.insertExecution(executionId, userId, conversationId, messageId, scopeId, bindingId, completionProtocol);
        var selectedModel = modelCatalog.accept(request.modelId(), request.reasoningMode());
        messages.setModelChoice(userId, executionId, modelCatalog.encode(selectedModel));
        finalDeliveries.initialize(userId, messageId, executionId);
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
        var execution = new ResponseExecutionView(executionId, messageId, ResponseExecutionStatus.QUEUED,
                0, null, null, java.util.List.of("STOP"));
        var message = new MessageView(messageId, sequence, "USER", request.text(), 0, true,
                null, executionId, null, now.atOffset(ZoneOffset.UTC).toString(),
                request.artifactIds().stream().map(id -> artifacts.get(userId, id)).toList());
        appendEvent(userId, conversation, commandId, ConversationDeltaType.MESSAGE_ACCEPTED.code(),
                new MessageAcceptedPayload(ConversationDeltaType.SCHEMA_VERSION, message, execution), now);
        appendEvent(userId, conversation, commandId, ConversationDeltaType.EXECUTION_QUEUED.code(),
                new ExecutionChangedPayload(ConversationDeltaType.SCHEMA_VERSION, execution), now);
        committedEvents.publishEvent(new com.agentsquad.squad.domain.model.CommandReadySignal(commandId));
        MessageSubmissionDO submission = messages.findSubmission(userId, scopeKey, key);
        log.info("用户消息已受理 {}", observations.logFields(Map.of(
                "conversationId", conversationId, "messageId", messageId, "executionId", executionId,
                "scopeId", scopeId, "commandId", commandId, "sequence", sequence,
                "modelId", selectedModel.modelId(), "reasoningMode", selectedModel.reasoningMode().name(),
                "preview", preview(request.text()))));
        return submission;
    }

    /**
     * 日志里只放单行截断的消息摘要，不输出完整用户输入。
     */
    private static String preview(String text) {
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 40 ? singleLine : singleLine.substring(0, 40) + "…";
    }

    private ConversationDO lockConversation(String userId, String conversationId) {
        ConversationDO row = conversations.lockOwned(userId, conversationId);
        if (row == null || !"ACTIVE".equals(row.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "对话不存在或已删除");
        }
        return row;
    }

    private void appendEvent(String userId, ConversationDO conversation, String commandId, String type,
                             Object payload, LocalDateTime now) {
        if (messages.reserveEvent(userId, conversation.conversationId()) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话公开水位已失效");
        }
        messages.insertEvent(userId, conversation.conversationId(), conversation.generation(),
                messages.eventSequence(userId, conversation.conversationId()), "command:" + commandId + ":" + type,
                type, RuntimePayloads.toJson(objectMapper, payload), now);
        committedEvents.publishEvent(new ConversationCommittedSignal(userId, conversation.conversationId()));
    }

    private String fallbackTitle(String text) {
        String title = com.agentsquad.squad.domain.model.ConversationTitles.safe(text, 32);
        return title.isEmpty() ? "新对话" : title;
    }
}
