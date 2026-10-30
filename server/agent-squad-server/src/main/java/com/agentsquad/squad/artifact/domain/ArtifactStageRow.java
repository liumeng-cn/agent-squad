package com.agentsquad.squad.artifact.domain;


/**
 * 隔离产物暂存记录。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record ArtifactStageRow(String stageId, String userId, String scopeId, String bindingId, String executionId,
                               String workspaceKey, String fileName, String sha256, byte[] content,
                               String status, String artifactId) {
}
