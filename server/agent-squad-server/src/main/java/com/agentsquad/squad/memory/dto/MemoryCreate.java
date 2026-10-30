package com.agentsquad.squad.memory.dto;

/**
 * 用户显式创建记忆请求。
 * <p>
 * 设置页的确认操作即写入授权；身份取登录态而非请求字段。
 * 同用户有效记忆键唯一；旧记录删除后可以同键创建新记忆身份，旧删除记录保持不变。
 */
public record MemoryCreate(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String key,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 8000) String content,
        java.time.OffsetDateTime expiresAt) {
}
