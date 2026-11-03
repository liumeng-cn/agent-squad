package com.agentsquad.squad.gateway.auth;

/** 管理员对用户的乐观锁更新。 */
public record UserUpdateRequest(long expectedVersion, String displayName, String role, String status) {
}
