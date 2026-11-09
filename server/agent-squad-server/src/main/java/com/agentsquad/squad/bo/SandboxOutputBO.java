package com.agentsquad.squad.bo;


/**
 * 沙箱输出文件的已校验内容。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxOutputBO(String path, byte[] content) {
}
