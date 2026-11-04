package com.agentsquad.squad.gateway.admin;

import com.agentsquad.squad.common.identity.AccountRole;
import com.agentsquad.squad.common.identity.AccountStatus;

/**
 * 管理员更新账号请求。
 *
 * <p>按 {@code expectedVersion} 做 CAS；为空的字段保持原值。</p>
 */
public record AdminUserUpdateRequest(Long expectedVersion, String displayName, AccountRole role, AccountStatus status) {
}
