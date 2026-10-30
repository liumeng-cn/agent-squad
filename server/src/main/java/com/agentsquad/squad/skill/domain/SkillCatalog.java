package com.agentsquad.squad.skill.domain;

import java.util.List;

/**
 * 执行固定的个人技能目录。
 * <p>
 * 只包含可发现描述，正文必须通过受控读取按需取得。
 */
public record SkillCatalog(String scopeId, List<SkillReference> skills) {
}
