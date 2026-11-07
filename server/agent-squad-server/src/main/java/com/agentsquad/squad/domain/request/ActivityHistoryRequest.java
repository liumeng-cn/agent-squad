package com.agentsquad.squad.domain.request;

/**
 * 消息活动历史查询
 * <p>
 * 使用上页最后一条活动的业务编号作为游标。
 */
public record ActivityHistoryRequest(String sourceMessageId, String beforeActivityId) {
}
