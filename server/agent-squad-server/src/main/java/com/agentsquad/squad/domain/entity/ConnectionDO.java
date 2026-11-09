package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 连接凭据引用的数据库查询结果。secret_ref 指向保险库条目，行内不含明文密钥
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionDO {
    /** 存储主键ID */
    private long id;
    /** 连接业务ID */
    private String connectionId;
    /** 资源归属用户ID */
    private String userId;
    /** 显示名称 */
    private String name;
    /** 提供方编码 */
    private String provider;
    /** 环境标识 */
    private String environment;
    /** 受控资源范围 */
    private String resourcePolicyJson;
    /** 密钥管理引用；不含明文 */
    private String secretRef;
    /** 创建幂等键；同键同用户复用原连接 */
    private String createIdempotencyKey;
    /** 配置授权版本 */
    private long revision;
    /** 是否启用[0/1] */
    private boolean enabled;
    /** 创建人ID */
    private String creatorId;
    /** 创建人姓名 */
    private String creatorName;
    /** 最后更新人ID */
    private String lastUpdaterId;
    /** 最后更新人姓名 */
    private String lastUpdaterName;
}
