package com.agentsquad.squad.domain.entity;


/**
 * 技能授权数据库记录
 * <p>
 * 启停控制新执行是否可选，安全撤销由版本授权代际控制。
 */
public record SkillGrantRow(String userId, String skillVersionId, boolean enabled, long version) {
}
