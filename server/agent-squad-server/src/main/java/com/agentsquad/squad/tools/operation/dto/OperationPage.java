package com.agentsquad.squad.tools.operation.dto;

import java.util.List;

/**
 * 目标操作账本分页视图；nextCursor 为最后一条的存储游标，null 表示没有更多。
 */
public record OperationPage(List<OperationView> items, String nextCursor) {
}
