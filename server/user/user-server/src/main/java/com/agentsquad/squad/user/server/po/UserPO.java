package com.agentsquad.squad.user.server.po;

import com.agentsquad.squad.common.enums.UserStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户持久化模型
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Data
@TableName(value = "sq_user", schema = "squad_user")
public class UserPO {
    /**
     * 存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 用户业务ID
     */
    private Long userId;
    /**
     * 自动生成用户名
     */
    private String username;
    /**
     * 注册手机号
     */
    private String phone;
    /**
     * 自定义昵称
     */
    private String displayName;
    /**
     * 账号状态
     */
    private UserStatus status;
    /**
     * 认证版本
     */
    private Long authVersion;
    /**
     * 资料版本
     */
    private Long version;
    /**
     * 创建时间
     */
    private Instant createTime;
    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
    /**
     * 删除标记
     */
    private String delFlag;
}
