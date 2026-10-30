package com.agentsquad.squad.sandbox.domain;

/**
 * 已校验归属的沙箱输入文件。
 * <p>
 * 仅携带隔离空间相对路径与受控内容，不暴露宿主路径，二进制保持原字节摘要。
 */
public record SandboxInputFile(String path, byte[] content) {
    public SandboxInputFile {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
