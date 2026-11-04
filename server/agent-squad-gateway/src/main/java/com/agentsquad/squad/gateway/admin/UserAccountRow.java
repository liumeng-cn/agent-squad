package com.agentsquad.squad.gateway.admin;

import com.agentsquad.squad.common.enums.identity.AccountRole;
import com.agentsquad.squad.common.enums.identity.AccountStatus;

import java.time.LocalDateTime;

/**
 * 用户账号持久化投影。
 *
 * <p>包含密码摘要供认证与改密校验使用，禁止直接作为接口响应返回。</p>
 */
public record UserAccountRow(String userId, String username, String displayName, String passwordHash,
                             AccountRole role, AccountStatus status, long authVersion, boolean mustChangePassword,
                             long version, LocalDateTime createTime) {
}
