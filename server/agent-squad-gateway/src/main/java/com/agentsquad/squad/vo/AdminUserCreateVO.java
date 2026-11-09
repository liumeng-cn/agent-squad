package com.agentsquad.squad.vo;

/**
 * 创建账号结果；临时密码只在此响应出现一次，禁止日志记录。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AdminUserCreateVO {

    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    private final String userId;

    @com.fasterxml.jackson.annotation.JsonProperty("temporaryPassword")
    private final String temporaryPassword;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AdminUserCreateVO(
            @com.fasterxml.jackson.annotation.JsonProperty("userId") String userId,
            @com.fasterxml.jackson.annotation.JsonProperty("temporaryPassword") String temporaryPassword) {
        this.userId = userId;
        this.temporaryPassword = temporaryPassword;
    }
}
