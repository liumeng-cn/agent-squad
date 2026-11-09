package com.agentsquad.squad.dto;

/** 已登录用户修改密码。 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class PasswordChangeDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("currentPassword")
    private final String currentPassword;

    @com.fasterxml.jackson.annotation.JsonProperty("newPassword")
    private final String newPassword;

    @com.fasterxml.jackson.annotation.JsonCreator
    public PasswordChangeDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("currentPassword") String currentPassword,
            @com.fasterxml.jackson.annotation.JsonProperty("newPassword") String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }
}
