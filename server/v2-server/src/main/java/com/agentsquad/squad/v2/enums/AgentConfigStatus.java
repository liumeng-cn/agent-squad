package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * Agent配置枚举
 * <p>
 * 表示 Agent 配置管理使用的有限值。
 */
@Getter
public enum AgentConfigStatus {
    ENABLED("启用"),
    DISABLED("停用");

    private final String desc;

    AgentConfigStatus(String desc) {
        this.desc = desc;
    }
}
