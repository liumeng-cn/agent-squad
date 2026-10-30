package com.agentsquad.squad.runtime.dto;

import com.agentsquad.squad.memory.dto.MemoryContextSnapshot;
import com.agentsquad.squad.skill.domain.SkillCatalog;

/**
 * 执行固定资料视图。
 * <p>
 * 技能只暴露可发现目录，记忆作为有版本来源的资料；两者都不能扩大执行工具权限。
 */
public record ExecutionContextSnapshot(SkillCatalog skills, MemoryContextSnapshot memory) {
}
