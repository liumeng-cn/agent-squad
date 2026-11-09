package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.domain.enums.WorkerDefinitionStatus;
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
                                       String runtimeProfileKey, WorkerDefinitionStatus status, long version) {
}
