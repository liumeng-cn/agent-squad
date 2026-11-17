package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.enums.ActivityGroup;
import com.agentsquad.squad.enums.ActivityType;

/**
 * 工具活动展示分类
 * <p>
 * 分类由服务端工具注册名决定，文案是固定模板，不采用模型自报的进度说明。
 */
public record ActivityClassificationBO(ActivityType type, ActivityGroup group, String summary) {
}
