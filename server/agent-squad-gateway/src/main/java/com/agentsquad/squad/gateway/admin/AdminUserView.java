package com.agentsquad.squad.gateway.admin;

import com.agentsquad.squad.domain.enums.AccountRole;
import com.agentsquad.squad.domain.enums.AccountStatus;

import java.time.LocalDateTime;

/**
 * 管理员可见的账号摘要。
 *
 * <p>不包含密码摘要、会话令牌或来源 IP。</p>
 */
public record AdminUserView(String userId, String username, String displayName, AccountRole role, AccountStatus status,
                            long version, boolean mustChangePassword, LocalDateTime createTime) {

    public static AdminUserView from(UserAccountRow row) {
        return new AdminUserView(row.userId(), row.username(), row.displayName(), row.role(), row.status(),
                row.version(), row.mustChangePassword(), row.createTime());
    }
}
