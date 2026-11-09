package com.agentsquad.squad.bo;

/**
 * 官方技能资源加载协议。
 * <p>
 * 将SDK动态工具参数转换为明确输入，仅用于选择当前目录内的技能和包内相对路径。
 */
public record SkillResourceRequestBO(String skillId, String path) {
}
