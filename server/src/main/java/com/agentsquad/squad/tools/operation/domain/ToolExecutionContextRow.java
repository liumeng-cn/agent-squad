package com.agentsquad.squad.tools.operation.domain;

/**
 * 工具调用时的目标与绑定上下文；由平台锁定读取，模型不能指定。
 * Master 直连流程无 Worker 节点，操作账本中的节点 ID 记为空。
 */
public record ToolExecutionContextRow(String taskId, String bindingId,
                                      String taskStatus, boolean cancellationRequested) {
}
