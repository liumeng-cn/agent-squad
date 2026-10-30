package com.agentsquad.squad.presentation.dto;

import java.util.List;

/**
 * 目标交互分页视图；nextCursor 为最后一条的存储游标，null 表示没有更多。
 */
public record InteractionPage(List<Interaction> items, String nextCursor) {
}
