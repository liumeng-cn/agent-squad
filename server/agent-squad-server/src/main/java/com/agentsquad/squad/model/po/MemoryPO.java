package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 记忆存储记录。
 * <p>
 * 保存用户显式内容、来源、乐观版本与删除墓碑；不直接作为接口响应。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_memory")
public class MemoryPO {
    /** 记忆业务ID */
    private String memoryId;
    /** 归属用户ID */
    private String userId;
    /** 记忆唯一键 */
    private String key;
    /** 显式记忆正文，删除后清空 */
    private String content;
    /** 记忆状态[ACTIVE,DELETED] */
    private String status;
    /** 记忆内容乐观版本 */
    private long version;
    /** 可选失效时间 */
    private java.time.LocalDateTime expiresAt;
    /** 授权用户消息ID */
    private String sourceMessageId;
    /** 用户显式授权原话片段 */
    private String sourceQuote;
    /** 存储标识 */
    private long storageId;
}
