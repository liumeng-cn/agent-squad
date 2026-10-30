package com.agentsquad.squad.runtime;

import com.agentsquad.squad.identity.AuthView;

/**
 * 业务HTTP测试的网关身份投影。
 *
 * 测试显式传入用户身份头，认证与CSRF验证由网关测试覆盖。
 */
record MessageTestActor(AuthView view) {
}
