package com.agentsquad.squad.artifact.dto;


/**
 * 待提交产物的安全元数据。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record ArtifactStageView(String stageId, String executionId, String fileName, String sha256, long sizeBytes,
                                String status, String artifactId) {
}
