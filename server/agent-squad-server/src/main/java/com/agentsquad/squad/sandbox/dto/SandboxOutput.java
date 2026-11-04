package com.agentsquad.squad.sandbox.dto;


/**
 * 沙箱输出文件的已校验内容。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxOutput(String path, byte[] content) {
}
