package com.agentsquad.squad.skill.dto;

import java.util.List;

/**
 * 个人技能版本分页结果。
 * <p>
 * 按存储顺序读取稳定分页，不混入其他用户的同名技能。
 */
public record SkillPage(List<SkillVersionView> items, String nextCursor) {
}
