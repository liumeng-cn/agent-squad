package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.TaskResponse;

import java.util.List;

/**
 * 目标分页响应。
 */
public record GoalTaskPageResponse(List<TaskResponse> items, String nextCursor) {
}
