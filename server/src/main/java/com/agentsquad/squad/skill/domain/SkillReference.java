package com.agentsquad.squad.skill.domain;


/**
 * 执行固定的技能引用。
 * <p>
 * 记录实际版本、摘要及授权代际，不受后续发布或普通停用影响。
 */
public record SkillReference(String skillVersionId, String name, String version, String description, String contentHash,
                             long authorizationRevision) {
}
