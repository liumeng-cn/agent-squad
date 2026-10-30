package com.agentsquad.squad.skill.domain;


/**
 * 技能授权持久投影。
 * <p>
 * 启停控制新执行是否可选，安全撤销由版本授权代际控制。
 */
public record SkillGrantRow(String userId, String skillVersionId, boolean enabled, long version) {
}
