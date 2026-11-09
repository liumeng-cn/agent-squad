package com.agentsquad.squad.vo;

import com.agentsquad.squad.po.UserAccountPO;

import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.AccountStatus;

import java.time.LocalDateTime;

/**
 * 管理员可见的账号摘要。
 *
 * <p>不包含密码摘要、会话令牌或来源 IP。</p>
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AdminUserVO {

    /** 业务用户 ID 在 Java 内为 Long，JSON 输出十进制字符串以免浏览器精度丢失。 */
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private final Long userId;

    @com.fasterxml.jackson.annotation.JsonProperty("username")
    private final String username;

    @com.fasterxml.jackson.annotation.JsonProperty("displayName")
    private final String displayName;

    @com.fasterxml.jackson.annotation.JsonProperty("role")
    private final AccountRole role;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final AccountStatus status;

    @com.fasterxml.jackson.annotation.JsonProperty("version")
    private final long version;

    @com.fasterxml.jackson.annotation.JsonProperty("mustChangePassword")
    private final boolean mustChangePassword;

    @com.fasterxml.jackson.annotation.JsonProperty("createTime")
    private final LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AdminUserVO(
            @com.fasterxml.jackson.annotation.JsonProperty("userId") Long userId,
            @com.fasterxml.jackson.annotation.JsonProperty("username") String username,
            @com.fasterxml.jackson.annotation.JsonProperty("displayName") String displayName,
            @com.fasterxml.jackson.annotation.JsonProperty("role") AccountRole role,
            @com.fasterxml.jackson.annotation.JsonProperty("status") AccountStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("version") long version,
            @com.fasterxml.jackson.annotation.JsonProperty("mustChangePassword") boolean mustChangePassword,
            @com.fasterxml.jackson.annotation.JsonProperty("createTime") LocalDateTime createTime) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.status = status;
        this.version = version;
        this.mustChangePassword = mustChangePassword;
        this.createTime = createTime;
    }

    public static AdminUserVO from(UserAccountPO row) {
        return new AdminUserVO(row.userId(), row.username(), row.displayName(), row.role(), row.status(),
                row.version(), row.mustChangePassword(), row.createTime());
    }
}
