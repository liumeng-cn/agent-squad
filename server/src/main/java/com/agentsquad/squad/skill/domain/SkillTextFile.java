package com.agentsquad.squad.skill.domain;

/**
 * 技能包内的文本资料。
 * <p>
 * 表达已规范化的相对路径与内容，独立于HTTP请求响应和持久化行模型。
 */
public record SkillTextFile(String path, String content) {
}
