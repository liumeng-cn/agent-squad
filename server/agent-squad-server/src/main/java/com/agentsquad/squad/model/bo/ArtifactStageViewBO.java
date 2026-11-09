package com.agentsquad.squad.model.bo;


/**
 * 待提交产物的安全元数据。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record ArtifactStageViewBO(Long stageId, Long executionId, String fileName, String sha256, long sizeBytes,
                                  String status, Long artifactId) {
}
