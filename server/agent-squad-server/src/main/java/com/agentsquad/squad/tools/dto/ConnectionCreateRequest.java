package com.agentsquad.squad.tools.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 创建连接请求；secret 只写不读，服务端加密入保险库。
 */
public record ConnectionCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 80) String provider,
        @NotBlank @Size(max = 80) String environment,
        @NotNull Map<String, Object> resourcePolicy,
        @NotBlank @Size(max = 16384) String secret) {
}
