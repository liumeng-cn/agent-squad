package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.ConnectionResponse;

import java.util.List;

/**
 * 连接分页响应；nextCursor 为空表示没有更多。
 */
public record ConnectionPageResponse(List<ConnectionResponse> items, String nextCursor) {
}
