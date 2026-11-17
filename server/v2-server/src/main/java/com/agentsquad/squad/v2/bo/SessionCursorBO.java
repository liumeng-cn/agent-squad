package com.agentsquad.squad.v2.bo;

import lombok.Data;


/**
 * 会话会话分页游标
 * <p>
 * v2会话列表的固定结构模型，字段不包含原始提示词或工具结果
 */
@Data
public class SessionCursorBO {
    /**
     * 游标格式版本
     */
    private int formatVersion;
    /**
     * 上一页最后活动时间
     */
    private java.time.Instant time;
    /**
     * 上一页最后主键
     */
    private Long id;
    /**
     * 分页时间上界
     */
    private java.time.Instant anchorTime;
}
