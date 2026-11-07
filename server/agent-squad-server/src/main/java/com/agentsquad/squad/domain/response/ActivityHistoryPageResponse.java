package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.ExecutionActivityView;

import java.util.List;

/**
 * 消息活动历史页
 * <p>
 * 按活动创建顺序倒序返回，游标为空表示已到末页。
 */
public record ActivityHistoryPageResponse(List<ExecutionActivityView> items, String nextCursor) {
}
