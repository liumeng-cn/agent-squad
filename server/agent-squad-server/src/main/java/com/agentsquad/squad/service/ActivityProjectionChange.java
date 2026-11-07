package com.agentsquad.squad.service;

import com.agentsquad.squad.common.enums.execution.ActivityStatus;
import com.agentsquad.squad.common.enums.execution.ActivityWaitReason;

/**
 * 单次原生边界对应的活动变化
 * <p>
 * 来源仅是模型或工具调用ID，分类与状态均由服务端映射，不包含调用参数。
 */
public record ActivityProjectionChange(String source, ActivityClassification classification,
                                       ActivityStatus status, ActivityWaitReason waitReason) {
}
