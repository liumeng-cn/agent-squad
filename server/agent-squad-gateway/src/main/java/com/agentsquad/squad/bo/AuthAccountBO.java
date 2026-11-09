package com.agentsquad.squad.bo;

import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.AccountStatus;

/**
 * 认证所需的账号投影；密码摘要只用于服务端校验，不进入响应。
 */
public record AuthAccountBO(Long userId, String username, String displayName, String passwordHash,
                            AccountRole role, AccountStatus status, long authVersion, boolean mustChangePassword,
                            long version) {
}
