package com.agentsquad.squad.worker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Worker 能力版本发布请求。
 * <p>
 * 输入输出 Schema 及工具白名单成为不可变版本正文，不携带执行实例状态。
 */
public record WorkerDefinitionCreateRequest(
        @NotBlank @Size(max = 80) String workerKey,
        @NotBlank @Size(max = 40) String workerVersion,
        @NotBlank @Size(max = 4000) String description,
        @NotNull JsonNode inputSchema,
        @NotNull JsonNode outputSchema,
        @NotNull @Size(max = 64) List<@NotBlank @Size(max = 100) String> toolAllowlist,
        @NotBlank @Size(max = 80) String modelProfileKey,
        @NotBlank @Size(max = 80) String runtimeProfileKey) {
}
