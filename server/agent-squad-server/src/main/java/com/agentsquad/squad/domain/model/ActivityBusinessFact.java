package com.agentsquad.squad.domain.model;

import com.agentsquad.squad.common.enums.execution.ActivityType;

/**
 * 已提交业务边界的活动来源
 * <p>
 * 稳定键来自上下文、原生运行或正式消息，不能使用模型自报的进度文案。
 */
public record ActivityBusinessFact(ActivityType type, String key) {
}
