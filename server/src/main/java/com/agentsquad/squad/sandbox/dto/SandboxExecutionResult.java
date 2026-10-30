package com.agentsquad.squad.sandbox.dto;


/**
 * 沙箱持久执行结果。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxExecutionResult(String executionId, String status, int exitCode, String stdout, String stderr,
                                     boolean truncated,
                                     java.util.List<com.agentsquad.squad.artifact.dto.ArtifactStageView> stagedArtifacts) {
}
