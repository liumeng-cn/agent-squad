package com.agentsquad.squad.skill.integration;

import java.util.List;

/**
 * 平台传入 SDK 的局部技能选择。
 * <p>
 * 由已验证上下文快照产生，模型不能自报；空集合表示该执行不获得任何技能。
 */
public record SkillSelection(List<String> skillVersionIds) {
    public SkillSelection {
        skillVersionIds = List.copyOf(skillVersionIds);
    }
}
