package com.agentsquad.squad.identity;

import com.agentsquad.squad.enums.AccountRole;

import java.util.List;

/**
 * 当前认证主体的最小公开信息。
 */
public record AuthView(Long userId, String username, List<AccountRole> roles, String csrfToken) {
}
