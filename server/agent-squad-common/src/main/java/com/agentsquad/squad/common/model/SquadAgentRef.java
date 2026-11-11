package com.agentsquad.squad.common.model;

import com.agentsquad.squad.enums.SquadAgentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SquadMsg 中的 Agent 实例身份及父子归属信息。
 *
 * <p>agentId 标识 Agent 定义，agentInstanceId 标识本次创建的实例。
 * parentAgentInstanceId 为空表示根 Agent；rootAgentInstanceId 指向本轮执行树的根实例。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SquadAgentRef {

    /**
     * Agent 定义 ID。
     */
    private String agentId;

    /**
     * 本次创建的 Agent 实例 ID。
     */
    private String agentInstanceId;

    /**
     * Agent 展示名称。
     */
    private String agentName;

    /**
     * Agent 在执行树中的角色。
     */
    private SquadAgentType agentType;

    /**
     * 父 Agent 实例 ID，根 Agent 为空。
     */
    private String parentAgentInstanceId;

    /**
     * 根 Agent 实例 ID。
     */
    private String rootAgentInstanceId;

    /**
     * 当前 Agent 在执行树中的层级，根节点为 0。
     */
    private Integer depth;
}
