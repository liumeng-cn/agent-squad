package com.agentsquad.squad.domain.entity;


/**
 * 执行固定的技能引用。
 * <p>
 * 记录实际版本、摘要及授权代际，不受后续发布或普通停用影响。
 */
public record SkillReferenceDO(String skillVersionId, String name, String version, String description, String contentHash,
                             long authorizationRevision) {
}
