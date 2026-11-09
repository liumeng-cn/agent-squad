package com.agentsquad.squad.dto;

import com.agentsquad.squad.enums.AccountRole;

/**
 * 管理员创建账号请求；角色缺省为 USER，临时密码由服务端生成。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AdminUserCreateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("username")
    private final String username;

    @com.fasterxml.jackson.annotation.JsonProperty("displayName")
    private final String displayName;

    @com.fasterxml.jackson.annotation.JsonProperty("role")
    private final AccountRole role;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AdminUserCreateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("username") String username,
            @com.fasterxml.jackson.annotation.JsonProperty("displayName") String displayName,
            @com.fasterxml.jackson.annotation.JsonProperty("role") AccountRole role) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }
}
