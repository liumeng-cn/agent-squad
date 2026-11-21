package com.agentsquad.squad.user.server.po;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.agentsquad.squad.common.enums.RbacStatus;

import java.time.Instant;

/**
 * 管理角色记录
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
@TableName(value = "sq_role", schema = "squad_user")
public class RolePO {
    /**
     * 存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色业务ID
     */
    private String roleId;

    /**
     * 角色编码
     */
    private String code;

    /**
     * 中文名称
     */
    private String name;

    /**
     * 职责说明
     */
    private String description;

    /**
     * 启用状态
     */
    private RbacStatus status;

    /**
     * 是否内置
     */
    private Boolean builtIn;

    /**
     * 编辑版本
     */
    private Long version;

    /**
     * 应用版本
     */
    private Long appliedVersion;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 创建人名称
     */
    private String creatorName = "";

    /**
     * 创建时间
     */
    private Instant createTime;

    /**
     * 最后更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 最后更新人名称
     */
    private String lastUpdaterName = "";

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
