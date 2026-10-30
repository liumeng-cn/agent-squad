package com.agentsquad.squad.gateway.auth;

/**
 * 本地开发登录请求。
 */
public record LoginRequest(String username, String password) {
}
