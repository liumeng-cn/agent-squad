package com.agentsquad.squad.domain.entity;

/**
 * 连接凭据引用的数据库查询结果。secret_ref 指向保险库条目，行内不含明文密钥
 */
public record ConnectionRow(long id, String connectionId, String userId, String name,
                            String provider, String environment, String resourcePolicyJson,
                            String secretRef, String createIdempotencyKey, long revision,
                            boolean enabled, String creatorId, String creatorName,
                            String lastUpdaterId, String lastUpdaterName) {
}
