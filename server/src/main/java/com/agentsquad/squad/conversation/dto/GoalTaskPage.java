package com.agentsquad.squad.conversation.dto;

import java.util.List;

/**
 * 目标分页响应。
 */
public record GoalTaskPage(List<TaskResponse> items, String nextCursor) {
}
