package com.agentsquad.squad.tools.dto;

import java.util.Map;

/**
 * 连接公开视图；永远只回 secretPresent，不回密钥本体或 secret_ref。
 */
public record ConnectionResponse(String connectionId, String name, String provider,
                                 String environment, Map<String, Object> resourcePolicy,
                                 boolean secretPresent, long revision, boolean enabled) {
}
