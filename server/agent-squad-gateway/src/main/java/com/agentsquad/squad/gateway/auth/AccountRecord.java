package com.agentsquad.squad.gateway.auth;

/** 数据库账号投影；密码摘要只在服务端使用，不得序列化到 HTTP。 */
public record AccountRecord(String userId, String username, String displayName, String passwordHash,
                            String role, String status, long authVersion, boolean mustChangePassword, long version) {
}
