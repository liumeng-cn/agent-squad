package com.agentsquad.squad.artifact;

/**
 * 已提交产物的公开元数据。
 * <p>
 * 仅包含可展示名称、类型、摘要及有效期，不暴露存储位置与内容。
 */
public record ArtifactView(String artifactId, String fileName, String mediaType, long sizeBytes,
                           String sha256, String status, String expiresAt) {
}
