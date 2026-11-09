package com.agentsquad.squad.po;

import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.AccountStatus;

import java.time.LocalDateTime;

/**
 * 用户账号持久化投影。
 *
 * <p>包含密码摘要供认证与改密校验使用，禁止直接作为接口响应返回。</p>
 */
public record UserAccountPO(String userId, String username, String displayName, String passwordHash,
                             AccountRole role, AccountStatus status, long authVersion, boolean mustChangePassword,
                             long version, LocalDateTime createTime) {
}
