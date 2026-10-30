package com.agentsquad.squad.skill.dto;


/**
 * 技能不可变版本的公开信息。
 * <p>
 * 内容版本与用户启用状态分开维护，摘要可用于运行证据核对。
 */
public record SkillVersionView(String skillVersionId, String name, String version, String contentHash,
                               String description, String status) {
}
