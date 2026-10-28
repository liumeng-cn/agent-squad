package com.agentsquad.squad.artifact;

import java.time.LocalDateTime;

/**
 * 产物存储投影。
 * <p>
 * 小文件内容与元数据在同一数据库事务提交，READY 不存在文件先写或数据库先写的窗口。
 */
public record ArtifactRow(String artifactId, String userId, String fileName, String mediaType,
                          long sizeBytes, String sha256, String uploadKey, byte[] content,
                          LocalDateTime expiresAt) {
}
