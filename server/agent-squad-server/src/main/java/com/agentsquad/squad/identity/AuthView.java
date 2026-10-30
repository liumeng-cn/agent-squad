package com.agentsquad.squad.identity;

import java.util.List;

/**
 * 当前认证主体的最小公开信息。
 */
public record AuthView(String userId, String username, List<String> roles, String csrfToken) {
}
