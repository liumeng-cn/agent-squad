package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.SkillFile;

import java.util.List;

/**
 * 个人技能版本详情。
 * <p>
 * 正文仅对所属用户可见；授权单独返回以支持页面乐观更新。
 */
public record SkillDetailResponse(SkillVersionResponse skill, List<SkillFile> files, SkillGrantResponse grant) {
}
