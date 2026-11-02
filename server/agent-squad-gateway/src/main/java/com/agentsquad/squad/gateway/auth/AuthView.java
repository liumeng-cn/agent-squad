package com.agentsquad.squad.gateway.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * 当前认证主体的最小公开信息；授权代际只用于内部断言，不返回浏览器。
 */
public record AuthView(String userId, String username, List<String> roles, String csrfToken,
                       boolean mustChangePassword, @JsonIgnore long authVersion) {
}
