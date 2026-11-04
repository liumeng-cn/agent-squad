package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.common.enums.identity.AccountRole;
import com.agentsquad.squad.common.enums.identity.AccountStatus;

/**
 * 认证所需的账号投影；密码摘要只用于服务端校验，不进入响应。
 */
public record AuthAccount(String userId, String username, String displayName, String passwordHash,
                          AccountRole role, AccountStatus status, long authVersion, boolean mustChangePassword,
                          long version) {
}
