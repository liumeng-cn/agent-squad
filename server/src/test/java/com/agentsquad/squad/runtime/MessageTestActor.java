package com.agentsquad.squad.runtime;

import com.agentsquad.squad.identity.AuthView;
import jakarta.servlet.http.Cookie;

/**
 * 消息HTTP测试的登录身份。
 * <p>
 * Cookie与CSRF均由实际开发认证服务签发，不用自造身份头替代认证。
 */
record MessageTestActor(AuthView view, Cookie cookie) {
}
