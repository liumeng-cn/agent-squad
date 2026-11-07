package com.agentsquad.squad.domain.dto;


/**
 * 沙箱初始文本文件。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxFile(String path, String content) {
}
