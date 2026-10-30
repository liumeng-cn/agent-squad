package com.agentsquad.squad.sandbox.dto;


/**
 * Python 沙箱受控请求。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxExecuteRequest(String logicalKey, String script, java.util.List<SandboxFile> files,
                                    java.util.List<String> outputPaths, int timeoutSeconds,
                                    java.util.List<String> artifactIds) {
    public SandboxExecuteRequest {
        artifactIds = artifactIds == null ? java.util.List.of() : java.util.List.copyOf(artifactIds);
    }

    public SandboxExecuteRequest(String logicalKey, String script, java.util.List<SandboxFile> files,
                                 java.util.List<String> outputPaths, int timeoutSeconds) {
        this(logicalKey, script, files, outputPaths, timeoutSeconds, java.util.List.of());
    }
}
