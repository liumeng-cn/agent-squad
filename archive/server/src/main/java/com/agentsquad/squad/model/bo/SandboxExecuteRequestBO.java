package com.agentsquad.squad.model.bo;


/**
 * Python 沙箱受控请求。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxExecuteRequestBO(String logicalKey, String script, java.util.List<SandboxFileBO> files,
                                      java.util.List<String> outputPaths, int timeoutSeconds,
                                      java.util.List<Long> artifactIds) {
    public SandboxExecuteRequestBO {
        artifactIds = artifactIds == null ? java.util.List.of() : java.util.List.copyOf(artifactIds);
    }

    public SandboxExecuteRequestBO(String logicalKey, String script, java.util.List<SandboxFileBO> files,
                                   java.util.List<String> outputPaths, int timeoutSeconds) {
        this(logicalKey, script, files, outputPaths, timeoutSeconds, java.util.List.of());
    }
}
