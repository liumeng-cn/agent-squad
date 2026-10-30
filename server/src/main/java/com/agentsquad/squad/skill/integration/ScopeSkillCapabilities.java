package com.agentsquad.squad.skill.integration;

import java.util.Set;

/**
 * 平台签发的技能工具能力集合。
 * <p>
 * 从当前角色和 Worker 能力许可派生，不接受模型或 Skill 自报的权限。
 */
public record ScopeSkillCapabilities(Set<String> toolNames) {
    public ScopeSkillCapabilities {
        toolNames = Set.copyOf(toolNames);
    }
}
