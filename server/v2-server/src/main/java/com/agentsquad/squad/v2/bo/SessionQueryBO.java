package com.agentsquad.squad.v2.bo;

import lombok.Data;


/**
 * 会话查询条件
 * <p>
 * v2会话列表的固定结构模型，字段不包含原始提示词或工具结果
 */
@Data
public class SessionQueryBO {
    /**
     * 可信用户ID
     */
    private Long userId;
    /**
     * 时间上界
     */
    private java.time.Instant anchorTime;
    /**
     * 上一页时间
     */
    private java.time.Instant cursorTime;
    /**
     * 上一页主键
     */
    private Long cursorId;
    /**
     * 多读一条判断下一页
     */
    private int fetchSize;
}
