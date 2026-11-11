package com.agentsquad.squad.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AgentScope ExecutionIdentity 的平台传输副本，不依赖 SDK 类型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SquadExecutionRef {

    private String agentId;

    private String sessionId;

    private String turnId;

    private String runId;
}
