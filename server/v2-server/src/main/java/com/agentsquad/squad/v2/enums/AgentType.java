package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * Agent配置枚举
 * <p>
 * 表示 Agent 配置管理使用的有限值。
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
