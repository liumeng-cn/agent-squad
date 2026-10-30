package com.agentsquad.squad.memory.dto;

/**
 * 用户纠正记忆请求。
 * <p>
 * 必须携带读取时的版本，冲突不得覆盖新值。
 */
public record MemoryUpdate(@com.fasterxml.jackson.annotation.JsonProperty(value = "expectedVersion", required = true)
                           @jakarta.validation.constraints.Min(0) long expectedVersion,
                           @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 8000) String content,
                           java.time.OffsetDateTime expiresAt) {
}
