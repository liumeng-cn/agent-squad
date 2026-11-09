package com.agentsquad.squad.vo;

import com.agentsquad.squad.enums.AccountRole;

import java.util.List;

/**
 * 当前认证主体的最小公开信息。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AuthVO {

    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    private final String userId;

    @com.fasterxml.jackson.annotation.JsonProperty("username")
    private final String username;

    @com.fasterxml.jackson.annotation.JsonProperty("roles")
    private final List<AccountRole> roles;

    @com.fasterxml.jackson.annotation.JsonProperty("csrfToken")
    private final String csrfToken;

    @com.fasterxml.jackson.annotation.JsonProperty("mustChangePassword")
    private final boolean mustChangePassword;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AuthVO(
            @com.fasterxml.jackson.annotation.JsonProperty("userId") String userId,
            @com.fasterxml.jackson.annotation.JsonProperty("username") String username,
            @com.fasterxml.jackson.annotation.JsonProperty("roles") List<AccountRole> roles,
            @com.fasterxml.jackson.annotation.JsonProperty("csrfToken") String csrfToken,
            @com.fasterxml.jackson.annotation.JsonProperty("mustChangePassword") boolean mustChangePassword) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
        this.csrfToken = csrfToken;
        this.mustChangePassword = mustChangePassword;
    }
}
