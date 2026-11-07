package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.WorkerDefinitionResponse;

import java.util.List;

/**
 * Worker 能力定义分页视图。
 * <p>
 * 游标对应持久主键，供客户端连续读取已发布版本。
 */
public record WorkerDefinitionPageResponse(List<WorkerDefinitionResponse> items, String nextCursor) {
}
