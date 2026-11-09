package com.agentsquad.squad.po;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 产物存储记录
 * <p>
 * 小文件内容与元数据在同一数据库事务提交，READY 不存在文件先写或数据库先写的窗口。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactPO {
    /** 产物业务ID */
    private String artifactId;
    /** 归属用户ID */
    private String userId;
    /** 安全展示文件名 */
    private String fileName;
    /** 服务端判定媒体类型 */
    private String mediaType;
    /** 内容字节数 */
    private long sizeBytes;
    /** 内容SHA256摘要 */
    private String sha256;
    /** 归属用户内的上传幂等键 */
    private String uploadKey;
    /** 文件二进制内容 */
    private byte[] content;
    /** 读取有效期 */
    private LocalDateTime expiresAt;
}
