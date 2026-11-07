package com.agentsquad.squad.domain.response;

import java.util.List;

/**
 * 目标交互分页视图；nextCursor 为最后一条的存储游标，null 表示没有更多。
 */
public record InteractionPageResponse(List<InteractionResponse> items, String nextCursor) {
}
