package com.agentsquad.squad.domain.response;


/**
 * 用户对技能版本的使用授权。
 * <p>
 * version 用于启停与撤销的并发检查，不改变技能正文。
 */
public record SkillGrantResponse(String skillVersionId, boolean enabled, long version) {
}
