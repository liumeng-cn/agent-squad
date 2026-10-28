package com.agentsquad.squad.tools.operation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 操作账本的公开摘要。
 *
 * 列表不返回执行参数、原生会话或连接凭据，详细核对由单独的受权视图提供。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationView(String operationId, String taskId, String jobId, String toolKey,
                            String effect, String status, String requestHash, long version, String summary) {
}
