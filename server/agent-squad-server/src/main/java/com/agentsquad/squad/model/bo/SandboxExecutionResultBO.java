package com.agentsquad.squad.model.bo;


/**
 * 沙箱持久执行结果。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxExecutionResultBO(String executionId, String status, int exitCode, String stdout, String stderr,
                                     boolean truncated,
                                     java.util.List<ArtifactStageViewBO> stagedArtifacts) {
}
