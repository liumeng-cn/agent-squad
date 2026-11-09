package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("sq_connection")
public class ConnectionPO {
    /**
     * 存储主键ID
     */
    @TableId(type = IdType.AUTO)
    private long id;
    /**
     * 连接业务ID
     */
    @TableField("connection_id")
    private String connectionId;
    /**
     * 资源归属用户ID
     */
    @TableField("user_id")
    private String userId;
    /**
     * 显示名称
     */
    private String name;
    /**
     * 提供方编码
     */
    private String provider;
    /**
     * 环境标识
     */
    private String environment;
    /**
     * 受控资源范围
     */
    @TableField("resource_policy_json")
    private String resourcePolicyJson;
    /**
     * 密钥管理引用；不含明文
     */
    @TableField("secret_ref")
    private String secretRef;
    /**
     * 创建幂等键；同键同用户复用原连接
     */
    @TableField("create_idempotency_key")
    private String createIdempotencyKey;
    /**
     * 配置授权版本
     */
    private long revision;
    /**
     * 是否启用[0/1]
     */
    private boolean enabled;
    /**
     * 创建人ID
     */
    @TableField("creator_id")
    private String creatorId;
    /**
     * 创建人姓名
     */
    @TableField("creator_name")
    private String creatorName;
    /**
     * 最后更新人ID
     */
    @TableField("last_updater_id")
    private String lastUpdaterId;
    /**
     * 最后更新人姓名
     */
    @TableField("last_updater_name")
    private String lastUpdaterName;
}
