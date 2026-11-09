package com.agentsquad.squad.vo;

/**
 * 创建账号结果；临时密码只在此响应出现一次，禁止日志记录。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AdminUserCreateVO {

    /** 业务用户 ID 在 Java 内为 Long，JSON 输出十进制字符串以免浏览器精度丢失。 */
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private final Long userId;

    @com.fasterxml.jackson.annotation.JsonProperty("temporaryPassword")
    private final String temporaryPassword;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AdminUserCreateVO(
            @com.fasterxml.jackson.annotation.JsonProperty("userId") Long userId,
            @com.fasterxml.jackson.annotation.JsonProperty("temporaryPassword") String temporaryPassword) {
        this.userId = userId;
        this.temporaryPassword = temporaryPassword;
    }
}
