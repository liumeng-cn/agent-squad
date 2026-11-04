package com.agentsquad.squad.integration.agentscope.completion;

/**
 * 模型结束证据缺口
 * <p>
 * 用有限分类表达缺失、冲突与结构错误，不向业务侧传递原始模型内容。
 */
public enum ModelEvidenceIssue {
    NONE,
    EVENT_GAP,
    EVENT_CONFLICT,
    MISSING_DISPATCH,
    MISSING_END,
    INVALID_LIFECYCLE,
    UNKNOWN_STATUS,
    MISSING_REASON,
    UNKNOWN_REASON,
    CONFLICTING_REASON,
    INVALID_PAYLOAD,
    MISSING_USAGE,
    INVALID_USAGE,
    CONFLICTING_USAGE
}
