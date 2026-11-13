package com.agentsquad.squad.v2.stream;

import com.agentsquad.squad.common.model.SquadExecutionRef;
import com.agentsquad.squad.common.model.SquadMsg;
import com.agentsquad.squad.enums.SquadEventCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.ExecutionIdentity;
import io.agentscope.core.event.AgentEvent;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * 将 AgentScope 新版 AgentEvent 转换为 Squad SSE 信封。
 *
 * <p>原生事件类型和原生字段不改名、不合并；平台只补充展示分类及会话关联信息。
 * 新增的 AgentScope 事件无需修改此适配器即可透传，未知事件归入 CUSTOM 分类但保留原始类型名。</p>
 */
public class AgentScopeEventAdapter {

    private static final String PROTOCOL_VERSION = "1";

    /**
     * 转换原生事件。conversationId 由平台会话上下文提供，不能从 SDK 字段推测。
     */
    public SquadMsg adapt(AgentEvent event, String conversationId, Long sequence,
                          ObjectMapper objectMapper) {

        Map<String, Object> payload = objectMapper.convertValue(event, new TypeReference<>() {
        });

        ExecutionIdentity identity = event.getExecution();
        SquadExecutionRef execution = identity == null ? null : new SquadExecutionRef(
                identity.agentId(), identity.sessionId(), identity.turnId(), identity.runId());

        Instant timestamp = parseTimestamp(event.getCreatedAt());
        String eventType = event.getType().getValue();

        SquadMsg message = new SquadMsg();
        message.setProtocolVersion(PROTOCOL_VERSION);
        message.setEventId(event.getId());
        message.setSequence(sequence);
        message.setEventType(eventType);
        message.setCategory(categoryOf(eventType));
        message.setConversationId(conversationId);
        message.setTurnId(identity == null ? null : identity.turnId());
        message.setNativeExecution(execution);
        message.setNativeSource(event.getSource());
        message.setPayload(payload);
        message.setTimestamp(timestamp);
        return message;
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException invalidTimestamp) {
            return Instant.now();
        }
    }

    private SquadEventCategory categoryOf(String eventType) {
        String type = eventType.toUpperCase(Locale.ROOT);
        if (type.startsWith("MODEL_CALL_")) return SquadEventCategory.MODEL;
        if (type.equals("AGENT_START") || type.equals("AGENT_END")) {
            return SquadEventCategory.LIFECYCLE;
        }
        if (type.startsWith("TEXT_BLOCK_") || type.equals("AGENT_RESULT")) {
            return SquadEventCategory.MESSAGE;
        }
        if (type.startsWith("THINKING_BLOCK_")) return SquadEventCategory.THINKING;
        if (type.startsWith("DATA_BLOCK_")) return SquadEventCategory.DATA;
        if (type.startsWith("TOOL_CALL_") || type.startsWith("TOOL_RESULT_")) {
            return SquadEventCategory.TOOL;
        }
        if (type.equals("SUBAGENT_EXPOSED")) return SquadEventCategory.SUBAGENT;
        if (type.equals("REQUIRE_USER_CONFIRM") || type.equals("USER_CONFIRM_RESULT")
                || type.equals("REQUIRE_EXTERNAL_EXECUTION")
                || type.equals("EXTERNAL_EXECUTION_RESULT")) {
            return SquadEventCategory.CONFIRMATION;
        }
        if (type.equals("REQUEST_STOP") || type.equals("EXCEED_MAX_ITERS")) {
            return SquadEventCategory.CONTROL;
        }
        if (type.equals("HINT_BLOCK")) return SquadEventCategory.HINT;
        if (type.equals("ALL_TOOLS_DENIED")) return SquadEventCategory.ERROR;
        if (type.equals("CUSTOM")) return SquadEventCategory.CUSTOM;
        return SquadEventCategory.CUSTOM;
    }
}
