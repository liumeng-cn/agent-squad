package com.agentsquad.squad.gateway;

import com.agentsquad.squad.bo.LoginResultBO;

/**
 * 测试用已登录会话：Cookie、CSRF 与临时密码状态。
 */
record AdminSession(String cookie, String csrf, boolean mustChangePassword) {

    static AdminSession from(LoginResultBO result) {
        return new AdminSession("SQUAD_SESSION=" + result.token(), result.view().csrfToken(),
                result.view().mustChangePassword());
    }
}
