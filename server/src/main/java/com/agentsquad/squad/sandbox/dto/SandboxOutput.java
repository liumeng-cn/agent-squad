package com.agentsquad.squad.sandbox.dto;


/**
 * 沙箱输出文件的已校验内容。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxOutput(String path, byte[] content) {
}
