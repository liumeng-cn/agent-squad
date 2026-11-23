package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * Agent 类型
 * <p>
 * 主 Agent 面向用户并可委派子 Agent；子 Agent 专注单一职责，只能被主 Agent 委派，自身不能再委派。
 */
@Getter
public enum AgentType {
    MASTER("主 Agent"),
    SUB_AGENT("子 Agent");

    private final String desc;

    AgentType(String desc) {
        this.desc = desc;
    }
}
