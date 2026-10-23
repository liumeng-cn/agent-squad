package com.agentsquad.squad.tools.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 更新连接请求；仅允许启停、资源策略与密钥轮换，expectedVersion 必填做乐观并发控制。
 */
public record ConnectionUpdateRequest(
        @NotNull Long expectedVersion,
        Boolean enabled,
        @Size(max = 16384) Map<String, Object> resourcePolicy,
        @Size(max = 16384) String secret) {
}
