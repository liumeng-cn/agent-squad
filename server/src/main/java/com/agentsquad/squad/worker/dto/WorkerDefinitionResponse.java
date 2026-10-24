package com.agentsquad.squad.worker.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Worker 能力定义公开视图。
 * <p>
 * 展示不可变定义、当前可调度状态和状态乐观版本。
 */
public record WorkerDefinitionResponse(String definitionId, String workerKey,
                                       String workerVersion, String description,
                                       JsonNode inputSchema, JsonNode outputSchema,
                                       List<String> toolAllowlist, String modelProfileKey,
                                       String runtimeProfileKey, String status, long version) {
}
