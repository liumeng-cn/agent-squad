package com.agentsquad.squad.domain.entity;


/**
 * 技能版本数据库记录
 * <p>
 * 记录用户归属、不可变元数据与安全撤销代际，不能当作接口 DTO。
 */
public record SkillVersionDO(long id, String skillVersionId, String userId, String name, String version,
                              String description, String contentHash, String requiredToolsJson, String status,
                              long authorizationRevision) {
}
