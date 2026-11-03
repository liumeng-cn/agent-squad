package com.agentsquad.squad.gateway.auth;

/**
 * 登录结果：原始会话令牌只返回给控制器写入 Cookie，服务端仅持久化摘要。
 */
public record LoginResult(String token, AuthView view) {
}
