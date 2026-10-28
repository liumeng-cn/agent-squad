package com.agentsquad.squad.tools.operation.dto;

import java.util.List;
import java.util.Map;

/**
 * 操作详情与服务端核对权限。
 * <p>
 * 参数只包含白名单字段的脱敏摘要，不可作为可编辑的执行参数副本。
 */
public record OperationDetail(OperationView operation, Map<String, Object> argumentsPreview,
                              String resultSummary, List<String> evidenceRefs, boolean canReconcile) {
}
