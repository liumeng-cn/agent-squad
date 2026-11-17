package com.agentsquad.squad.v2.vo;

import lombok.Data;


/**
 * 会话会话分页结果
 * <p>
 * v2会话列表的固定结构模型，字段不包含原始提示词或工具结果
 */
@Data
public class SessionPageVO {
    /**
     * 当前页
     */
    private java.util.List<SessionSummaryVO> items;
    /**
     * 下一页游标，可空
     */
    private String nextCursor;
    /**
     * 是否有更多记录
     */
    private boolean hasMore;
    /**
     * 服务端时间
     */
    private java.time.Instant serverTime;
}
