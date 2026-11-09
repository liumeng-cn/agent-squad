package com.agentsquad.squad.dto;

/**
 * 本地开发登录请求。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class LoginDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("username")
    private final String username;

    @com.fasterxml.jackson.annotation.JsonProperty("password")
    private final String password;

    @com.fasterxml.jackson.annotation.JsonCreator
    public LoginDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("username") String username,
            @com.fasterxml.jackson.annotation.JsonProperty("password") String password) {
        this.username = username;
        this.password = password;
    }
}
