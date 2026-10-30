package com.agentsquad.squad.skill.domain;

import java.util.List;

import com.agentsquad.squad.skill.domain.SkillTextFile;

/**
 * 已规范化的技能发布包。
 * <p>
 * 校验结果按路径排序，摘要涵盖元数据与完整文件文本。
 */
public record SkillPackage(String name, String version, String description, List<String> requiredTools,
                           List<SkillTextFile> files, String contentHash) {
}
