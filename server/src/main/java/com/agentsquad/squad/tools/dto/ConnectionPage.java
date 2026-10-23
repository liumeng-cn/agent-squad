package com.agentsquad.squad.tools.dto;

import java.util.List;

/**
 * 连接分页响应；nextCursor 为空表示没有更多。
 */
public record ConnectionPage(List<ConnectionResponse> items, String nextCursor) {
}
