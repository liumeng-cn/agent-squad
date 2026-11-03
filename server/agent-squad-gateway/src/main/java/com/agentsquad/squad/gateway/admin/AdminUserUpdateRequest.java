package com.agentsquad.squad.gateway.admin;

/**
 * 管理员更新账号请求。
 *
 * <p>按 {@code expectedVersion} 做 CAS；为空的字段保持原值。</p>
 */
public record AdminUserUpdateRequest(Long expectedVersion, String displayName, String role, String status) {
}
