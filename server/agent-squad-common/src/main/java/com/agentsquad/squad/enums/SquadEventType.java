package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * SquadMsg 支持的事件类型及其展示分类。
 *
 * <p>该枚举是平台传输协议的一部分。AgentScope 原生事件由转换层映射到这里，
 * Squad 自身产生的生命周期事件也使用同一套类型。</p>
 */
@Getter
public enum SquadEventType {
    TURN_STARTED(SquadEventCategory.LIFECYCLE),
    TURN_COMPLETED(SquadEventCategory.LIFECYCLE),
    TURN_FAILED(SquadEventCategory.LIFECYCLE),
    TURN_CANCELLED(SquadEventCategory.LIFECYCLE),

    AGENT_CREATED(SquadEventCategory.LIFECYCLE),
    AGENT_STARTED(SquadEventCategory.LIFECYCLE),
    AGENT_COMPLETED(SquadEventCategory.LIFECYCLE),
    AGENT_FAILED(SquadEventCategory.LIFECYCLE),
    AGENT_CANCELLED(SquadEventCategory.LIFECYCLE),

    MODEL_CALL_STARTED(SquadEventCategory.MODEL),
    MODEL_CALL_COMPLETED(SquadEventCategory.MODEL),
    MODEL_CALL_FAILED(SquadEventCategory.MODEL),

    MESSAGE_STARTED(SquadEventCategory.MESSAGE),
    TEXT_BLOCK_STARTED(SquadEventCategory.MESSAGE),
    TEXT_BLOCK_DELTA(SquadEventCategory.MESSAGE),
    TEXT_BLOCK_COMPLETED(SquadEventCategory.MESSAGE),
    MESSAGE_COMPLETED(SquadEventCategory.MESSAGE),

    THINKING_BLOCK_STARTED(SquadEventCategory.THINKING),
    THINKING_BLOCK_DELTA(SquadEventCategory.THINKING),
    THINKING_BLOCK_COMPLETED(SquadEventCategory.THINKING),

    TOOL_CALL_STARTED(SquadEventCategory.TOOL),
    TOOL_CALL_DELTA(SquadEventCategory.TOOL),
    TOOL_CALL_COMPLETED(SquadEventCategory.TOOL),
    TOOL_EXECUTION_STARTED(SquadEventCategory.TOOL),
    TOOL_RESULT_DELTA(SquadEventCategory.TOOL),
    TOOL_EXECUTION_COMPLETED(SquadEventCategory.TOOL),
    TOOL_EXECUTION_FAILED(SquadEventCategory.TOOL),

    SUBAGENT_CREATED(SquadEventCategory.SUBAGENT),
    SUBAGENT_STARTED(SquadEventCategory.SUBAGENT),
    SUBAGENT_COMPLETED(SquadEventCategory.SUBAGENT),
    SUBAGENT_FAILED(SquadEventCategory.SUBAGENT),

    EXECUTION_INTERRUPTED(SquadEventCategory.CONTROL),
    EXECUTION_RESUMED(SquadEventCategory.CONTROL),

    CONFIRMATION_REQUIRED(SquadEventCategory.CONFIRMATION),
    CONFIRMATION_RESOLVED(SquadEventCategory.CONFIRMATION),

    ERROR(SquadEventCategory.ERROR),
    CUSTOM(SquadEventCategory.CUSTOM);

    private final SquadEventCategory category;

    SquadEventType(SquadEventCategory category) {
        this.category = category;
    }
}
