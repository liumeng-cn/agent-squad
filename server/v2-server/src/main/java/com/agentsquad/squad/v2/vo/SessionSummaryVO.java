package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.SessionTitleSource;
import lombok.Data;

import java.time.Instant;

/**
 * 会话列表摘要
 * <p>
 * v2会话列表的固定结构模型，字段不包含原始提示词或工具结果
 */
@Data
public class SessionSummaryVO {
    /**
     * 会话ID
     */
    private String sessionId;
    /**
     * 会话标题
     */
    private String title;
    /**
     * 标题来源
     */
    private SessionTitleSource titleSource;
    /**
     * 标题版本
     */
    private long version;
    /**
     * 有受理的用户消息
     */
    private boolean hasMessages;
    /**
     * 建档时间
     */
    private Instant createdAt;
    /**
     * 最近新输入时间
     */
    private Instant lastActivityAt;
    /**
     * 运行观察摘要
     */
    private SessionExecutionVO execution;
}
