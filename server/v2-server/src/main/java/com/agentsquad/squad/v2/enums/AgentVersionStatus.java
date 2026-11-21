package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * Agent配置枚举
 * <p>
 * 表示 Agent 配置管理使用的有限值。
 */
@Getter
public enum AgentVersionStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布");

    private final String desc;

    AgentVersionStatus(String desc) {
        this.desc = desc;
    }
}
