package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.domain.model.SkillCatalog;

/**
 * 执行固定资料视图。
 * <p>
 * 技能只暴露可发现目录，记忆作为有版本来源的资料；两者都不能扩大执行工具权限。
 */
public record ExecutionContextSnapshot(SkillCatalog skills, MemoryContextSnapshot memory) {
}
