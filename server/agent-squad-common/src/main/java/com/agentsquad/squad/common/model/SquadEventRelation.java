package com.agentsquad.squad.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SquadMsg 事件之间的关联信息。
 *
 * <p>字段按事件实际需要填写，不适用的关联字段保持为空。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SquadEventRelation {

    /**
     * 直接关联的父事件 ID。
     */
    private String parentEventId;

    /**
     * 导致当前事件发生的事件 ID。
     */
    private String causedByEventId;

    /**
     * 消息 ID。
     */
    private String messageId;

    /**
     * 消息内容块 ID。
     */
    private String blockId;

    /**
     * 工具调用 ID。
     */
    private String toolCallId;

    /**
     * Agent 的一次具体执行 ID。
     */
    private String executionId;

    /**
     * 当前事件关联的目标 Agent 实例 ID。
     */
    private String targetAgentInstanceId;
}
