package com.agentsquad.squad.model.vo;

/**
 * 已提交产物的公开元数据。
 * <p>
 * 仅包含可展示名称、类型、摘要及有效期，不暴露存储位置与内容。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ArtifactVO {

    @com.fasterxml.jackson.annotation.JsonProperty("artifactId")
    private final Long artifactId;

    @com.fasterxml.jackson.annotation.JsonProperty("fileName")
    private final String fileName;

    @com.fasterxml.jackson.annotation.JsonProperty("mediaType")
    private final String mediaType;

    @com.fasterxml.jackson.annotation.JsonProperty("sizeBytes")
    private final long sizeBytes;

    @com.fasterxml.jackson.annotation.JsonProperty("sha256")
    private final String sha256;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final String status;

    @com.fasterxml.jackson.annotation.JsonProperty("expiresAt")
    private final String expiresAt;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ArtifactVO(
            @com.fasterxml.jackson.annotation.JsonProperty("artifactId") Long artifactId,
            @com.fasterxml.jackson.annotation.JsonProperty("fileName") String fileName,
            @com.fasterxml.jackson.annotation.JsonProperty("mediaType") String mediaType,
            @com.fasterxml.jackson.annotation.JsonProperty("sizeBytes") long sizeBytes,
            @com.fasterxml.jackson.annotation.JsonProperty("sha256") String sha256,
            @com.fasterxml.jackson.annotation.JsonProperty("status") String status,
            @com.fasterxml.jackson.annotation.JsonProperty("expiresAt") String expiresAt) {
        this.artifactId = artifactId;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.status = status;
        this.expiresAt = expiresAt;
    }
}
