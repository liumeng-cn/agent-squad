package com.agentsquad.squad.model.bo;

/**
 * 已校验归属的沙箱输入文件。
 * <p>
 * 仅携带隔离空间相对路径与受控内容，不暴露宿主路径，二进制保持原字节摘要。
 */
public record SandboxInputFileBO(String path, byte[] content) {
    public SandboxInputFileBO {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
