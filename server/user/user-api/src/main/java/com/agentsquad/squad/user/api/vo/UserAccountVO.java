package com.agentsquad.squad.user.api.vo;

import com.agentsquad.squad.common.enums.UserStatus;
import lombok.Data;

/**
 * 跨服务用户账号事实
 * <p>
 * 所属user/user-api模块，按明确职责维护用户身份与数据边界。
 */
@Data
public class UserAccountVO {
    /**
     * 稳定用户业务ID，按字符串传输
     */
    private String userId;
    /**
     * 自动生成的用户名
     */
    private String userName;
    /**
     * 用户手机号，仅认证和本人资料使用
     */
    private String phone;
    /**
     * 用户自定义昵称
     */
    private String displayName;
    /**
     * 昵称为空时回退用户名
     */
    private String effectiveDisplayName;
    /**
     * 当前账号状态
     */
    private UserStatus status;
    /**
     * 认证版本
     */
    private long authVersion;
    /**
     * 资料编辑版本
     */
    private long version;
}
