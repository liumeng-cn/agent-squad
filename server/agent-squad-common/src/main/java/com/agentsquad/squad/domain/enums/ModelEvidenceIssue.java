package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 模型结束证据缺口
 * <p>
 * 用有限分类表达缺失、冲突与结构错误，不向业务侧传递原始模型内容。
 */
@Getter
public enum ModelEvidenceIssue {
    NONE(1, "NONE", "无缺口"),
    EVENT_GAP(2, "EVENT_GAP", "事件缺口"),
    EVENT_CONFLICT(3, "EVENT_CONFLICT", "事件冲突"),
    MISSING_DISPATCH(4, "MISSING_DISPATCH", "缺少派发"),
    MISSING_END(5, "MISSING_END", "缺少结束"),
    INVALID_LIFECYCLE(6, "INVALID_LIFECYCLE", "生命周期非法"),
    UNKNOWN_STATUS(7, "UNKNOWN_STATUS", "未知状态"),
    MISSING_REASON(8, "MISSING_REASON", "缺少停止原因"),
    UNKNOWN_REASON(9, "UNKNOWN_REASON", "未知停止原因"),
    CONFLICTING_REASON(10, "CONFLICTING_REASON", "停止原因冲突"),
    INVALID_PAYLOAD(11, "INVALID_PAYLOAD", "载荷非法"),
    MISSING_USAGE(12, "MISSING_USAGE", "缺少用量"),
    INVALID_USAGE(13, "INVALID_USAGE", "用量非法"),
    CONFLICTING_USAGE(14, "CONFLICTING_USAGE", "用量冲突");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ModelEvidenceIssue(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
