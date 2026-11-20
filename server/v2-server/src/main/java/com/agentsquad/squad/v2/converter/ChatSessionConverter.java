package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.bo.ChatProjectionBO;
import com.agentsquad.squad.v2.enums.*;
import com.agentsquad.squad.v2.vo.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.observation.ActionObservation;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionInbox;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.session.AgentSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;

/**
 * 原生会话公开投影
 * <p>
 * 按已提交事件重建正文和工具状态，只解析协议载荷；不公开提示词、工具参数或原始结果。
 */
@Component
public class ChatSessionConverter {
    /**
     * 读取固定执行前缀并结合持久输入收据；不扫描未提交对象，不修改 SDK 状态。
     */
    public ChatSessionVO snapshot(AgentSession session, String sessionId) {
        var log = session.log();
        var head = log.head();
        var result = new ChatSessionVO();
        result.setSessionId(sessionId);
        result.setCursor(head.seq());
        var projection = new ChatProjectionBO();
        var messages = projection.getMessages();
        for (var event : log.scan(0, head.seq())) {
            if (NativeChatEventType.fromValue(event.type()) == NativeChatEventType.INPUT_STARTED) {
                projection.getStartedCommands().add(String.valueOf(event.data().get("commandId")));
            }
            apply(event, result, projection);
        }

        // 先读取输入游标再读视图；并发接受的新输入最多重复展示，不会被宣告为已消费。
        result.setInputCursor(log.inbox().head().seq());
        var inbox = new SessionInbox(log).snapshot();
        for (var command : inbox.commands()) {
            result.getAcceptedInputIds().add(command.id());
            if (NativeChatCommandType.fromValue(command.kind()) == NativeChatCommandType.SUBMIT) {
                addUserMessages(command.messages(), command.turnId(), messages);
                if (!projection.getStartedCommands().contains(command.id())
                        && !inbox.rejected().containsKey(command.id())
                        && (result.getTurnId() == null || result.getStatus() == ChatExecutionStatus.COMPLETED)) {
                    result.setTurnId(command.turnId());
                    result.setStatus(ChatExecutionStatus.QUEUED);
                }
            } else if (NativeChatCommandType.fromValue(command.kind()) == NativeChatCommandType.STEER) {
                var input = new ChatInputVO();
                input.setId(command.id());
                var messageIds = new ArrayList<String>();
                StringBuilder content = new StringBuilder();
                for (var message : command.messages()) {
                    messageIds.add(message.getId());
                    content.append(message.getTextContent());
                }
                input.setMessageIds(messageIds);
                input.setTurnId(command.turnId());
                input.setContent(content.toString());
                if (projection.getAppliedInputs().contains(command.id())) {
                    input.setStatus(ChatInputStatus.APPLIED);
                } else if (projection.getFinishedTurns().contains(command.turnId())
                        || inbox.rejected().containsKey(command.id())) {
                    input.setStatus(ChatInputStatus.UNAPPLIED);
                } else {
                    input.setStatus(ChatInputStatus.ACCEPTED);
                }
                result.getInputs().add(input);
                addUserMessages(command.messages(), command.turnId(), messages);
            }
        }
        if (result.getStatus() == ChatExecutionStatus.RUNNING
                && (head.owner() == null || head.leaseUntil() <= System.currentTimeMillis())) {
            result.setStatus(ChatExecutionStatus.INTERRUPTED);
            var reply = projection.getActiveReplies().get(result.getTurnId());
            if (reply != null) {
                reply.setStatus(ChatExecutionStatus.INTERRUPTED);
            }
        }
        result.getMessages().addAll(messages.values());
        return result;
    }

    private void apply(SessionEvent event, ChatSessionVO session, ChatProjectionBO projection) {
        var eventType = NativeChatEventType.fromValue(event.type());
        if (eventType == NativeChatEventType.UNKNOWN) {
            return;
        }
        var data = event.data();
        if (eventType == NativeChatEventType.INPUT_APPLIED && data.get("commandIds") instanceof Iterable<?> ids) {
            for (var id : ids) {
                projection.getAppliedInputs().add(String.valueOf(id));
            }
        }
        if (NativeChatEventType.fromValue(event.type()) == NativeChatEventType.USER_MESSAGE && data.get("message") != null) {
            var message = JsonUtils.getJsonCodec().convertValue(data.get("message"), Msg.class);
            addUserMessages(java.util.List.of(message), event.turnId(), projection.getMessages());
        }
        if (event.turnId() == null) {
            return;
        }
        if (NativeChatEventType.fromValue(event.type()) == NativeChatEventType.RUN_START) {
            session.setTurnId(event.turnId());
            session.setRunId(event.executionRunId());
            session.setStatus(ChatExecutionStatus.RUNNING);
            projection.getFinishedTurns().remove(event.turnId());
            // 恢复执行使用新的回复段，旧执行的中断状态和正文保留。
            projection.getActiveReplies().remove(event.turnId());
        }
        switch (NativeChatEventType.fromValue(event.type())) {
            case MODEL_PREPARED -> {
                if (NativeChatModelPurpose.fromValue(data.get("purpose")) == NativeChatModelPurpose.REASONING) {
                    startModelReply(event, projection, String.valueOf(data.get("modelCallId")));
                }
            }
            case MODEL_CHUNK -> appendChunk(event, projection);
            case TURN_OUTPUT -> {
                if (data.get("message") != null) {
                    var message = JsonUtils.getJsonCodec().convertValue(data.get("message"), Msg.class);
                    reply(event, projection).setContent(message.getTextContent());
                }
            }
            case TOOL_REQUESTED -> {
                var activity = new ChatActivityVO();
                activity.setId(String.valueOf(data.get("toolCallId")));
                var call = data.get("call");
                activity.setTitle(call instanceof Map<?, ?> value ? String.valueOf(value.get("name")) : "工具调用");
                activity.setStartedAt(event.occurredAt());
                reply(event, projection).getActivities().add(activity);
            }
            case ACTION_END -> {
                var id = String.valueOf(data.get("toolCallId"));
                for (var activity : reply(event, projection).getActivities()) {
                    if (activity.getId().equals(id)) {
                        activity.setStatus(toolStatus(data));
                        activity.setFinishedAt(event.occurredAt());
                    }
                }
            }
            case RUN_END, TURN_CANCELLED -> {
                var status = status(String.valueOf(data.get("status")));
                reply(event, projection).setStatus(status);
                reply(event, projection).setFinishedAt(event.occurredAt());
                for (var activity : reply(event, projection).getActivities()) {
                    if (activity.getStatus() == ChatExecutionStatus.RUNNING) {
                        activity.setStatus(ChatExecutionStatus.INTERRUPTED);
                        activity.setFinishedAt(event.occurredAt());
                    }
                }
                projection.getFinishedTurns().add(event.turnId());
                if (event.turnId().equals(session.getTurnId())) {
                    session.setStatus(status);
                }
            }
            default -> {
            }
        }
    }

    private void appendChunk(SessionEvent event, ChatProjectionBO projection) {
        var data = event.data();
        if (NativeChatModelPurpose.fromValue(data.get("purpose")) != NativeChatModelPurpose.REASONING || data.get("chunk") == null) {
            return;
        }
        if (!(data.get("chunk") instanceof Map<?, ?> chunk)
                || !(chunk.get("content") instanceof Iterable<?> blocks)) {
            return;
        }
        var reply = modelReply(event, projection, String.valueOf(data.get("modelCallId")));
        for (var raw : blocks) {
            if (!(raw instanceof Map<?, ?> block)) {
                continue;
            }
            var type = NativeChatContentType.fromValue(block.get("type"));
            if (type == NativeChatContentType.TEXT && block.get("text") instanceof String text) {
                reply.setContent(reply.getContent() + text);
            } else if (type == NativeChatContentType.THINKING
                    && block.get("thinking") instanceof String thinking && !thinking.isEmpty()) {
                var callId = String.valueOf(data.get("modelCallId"));
                var target = thinkingBlock(reply, callId);
                target.setContent(target.getContent() + thinking);
            }
        }
    }

    private ChatThinkingVO thinkingBlock(ChatHistoryMessageVO reply, String callId) {
        for (var block : reply.getThinkingBlocks()) {
            if (block.getModelCallId().equals(callId)) {
                return block;
            }
        }
        var block = new ChatThinkingVO();
        block.setModelCallId(callId);
        reply.getThinkingBlocks().add(block);
        return block;
    }

    private void addUserMessages(Iterable<Msg> inputs, String turnId, Map<String, ChatHistoryMessageVO> messages) {
        for (var message : inputs) {
            if (messages.containsKey(message.getId())) {
                continue;
            }
            var item = new ChatHistoryMessageVO();
            item.setId(message.getId());
            item.setTurnId(turnId);
            item.setRole(ChatMessageRole.USER);
            item.setContent(message.getTextContent());
            item.setStatus(ChatExecutionStatus.COMPLETED);
            messages.put(item.getId(), item);
        }
    }

    /**
     * 新模型回复独立追加；已显示的正文和思考不因插话或下一次推理而清空。
     */
    private void startModelReply(SessionEvent event, ChatProjectionBO projection, String callId) {
        var previous = projection.getActiveReplies().get(event.turnId());
        var id = modelReplyId(event.turnId(), callId);
        if (previous != null && !previous.getId().equals(id)
                && previous.getStatus() == ChatExecutionStatus.RUNNING) {
            previous.setStatus(ChatExecutionStatus.COMPLETED);
            previous.setFinishedAt(event.occurredAt());
        }
        modelReply(event, projection, callId);
    }

    private ChatHistoryMessageVO modelReply(SessionEvent event, ChatProjectionBO projection, String callId) {
        var id = modelReplyId(event.turnId(), callId);
        var message = projection.getMessages().get(id);
        if (message == null) {
            message = createReply(event, id);
            projection.getMessages().put(id, message);
        }
        projection.getActiveReplies().put(event.turnId(), message);
        return message;
    }

    private String modelReplyId(String turnId, String callId) {
        return "assistant:" + turnId + ":" + callId;
    }

    private ChatHistoryMessageVO reply(SessionEvent event, ChatProjectionBO projection) {
        var message = projection.getActiveReplies().get(event.turnId());
        if (message == null) {
            var id = "assistant:" + event.turnId() + ":" + event.executionRunId();
            message = createReply(event, id);
            projection.getMessages().put(id, message);
            projection.getActiveReplies().put(event.turnId(), message);
        }
        return message;
    }

    private ChatHistoryMessageVO createReply(SessionEvent event, String id) {
        var message = new ChatHistoryMessageVO();
        message.setId(id);
        message.setTurnId(event.turnId());
        message.setRole(ChatMessageRole.ASSISTANT);
        message.setStartedAt(event.occurredAt());
        return message;
    }

    private ChatExecutionStatus toolStatus(Map<String, Object> data) {
        if (data.get("result") instanceof Map<?, ?> result) {
            for (var state : ToolResultState.values()) {
                if (state == ToolResultState.RUNNING || !state.getValue().equals(result.get("state"))) {
                    continue;
                }
                return switch (state) {
                    case SUCCESS -> ChatExecutionStatus.COMPLETED;
                    case ERROR, DENIED -> ChatExecutionStatus.FAILED;
                    case INTERRUPTED -> ChatExecutionStatus.INTERRUPTED;
                    case RUNNING -> ChatExecutionStatus.RUNNING;
                };
            }
        }
        if (data.get("observation") instanceof Map<?, ?> observation) {
            for (var state : ActionObservation.Status.values()) {
                if (!state.name().equals(observation.get("status"))) {
                    continue;
                }
                return switch (state) {
                    case RETURNED -> ChatExecutionStatus.COMPLETED;
                    case FAILED, DENIED -> ChatExecutionStatus.FAILED;
                    case SUSPENDED -> ChatExecutionStatus.WAITING;
                    case INTERRUPTED -> ChatExecutionStatus.INTERRUPTED;
                    case STARTED -> ChatExecutionStatus.RUNNING;
                };
            }
        }
        return ChatExecutionStatus.INTERRUPTED;
    }

    private ChatExecutionStatus status(String value) {
        return switch (NativeChatStatus.fromValue(value)) {
            case COMPLETED -> ChatExecutionStatus.COMPLETED;
            case SUSPENDED -> ChatExecutionStatus.WAITING;
            case INTERRUPTED, CANCELLED -> ChatExecutionStatus.INTERRUPTED;
            default -> ChatExecutionStatus.FAILED;
        };
    }
}
