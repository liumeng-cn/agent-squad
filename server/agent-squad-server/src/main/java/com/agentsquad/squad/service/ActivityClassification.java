package com.agentsquad.squad.service;

import com.agentsquad.squad.common.enums.execution.ActivityGroup;
import com.agentsquad.squad.common.enums.execution.ActivityType;

/**
 * 工具活动展示分类
 * <p>
 * 分类由服务端工具注册名决定，文案是固定模板，不采用模型自报的进度说明。
 */
public record ActivityClassification(ActivityType type, ActivityGroup group, String summary) {
}
