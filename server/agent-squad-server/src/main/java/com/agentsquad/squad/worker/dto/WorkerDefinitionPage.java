package com.agentsquad.squad.worker.dto;

import java.util.List;

/**
 * Worker 能力定义分页视图。
 * <p>
 * 游标对应持久主键，供客户端连续读取已发布版本。
 */
public record WorkerDefinitionPage(List<WorkerDefinitionResponse> items, String nextCursor) {
}
