package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ChatExecutionStatus;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 聊天会话公开快照
 * <p>
 * 用于聊天接口公开传输，不暴露原始 SDK 状态和执行载荷。
 */
@Data
public class ChatSessionVO {
    /**
     * 持续对话标识。
     */
    private String sessionId;
    /**
     * 执行日志已提交游标。
     */
    private long cursor;
    /**
     * 输入日志已提交游标。
     */
    private long inputCursor;
    /**
     * 当前轮次，没有轮次时为空。
     */
    private String turnId;
    /**
     * 当前执行，恢复后可变化。
     */
    private String runId;
    /**
     * 原生状态投影。
     */
    private ChatExecutionStatus status = ChatExecutionStatus.IDLE;
    /**
     * 公开历史及当前回复。
     */
    private List<ChatHistoryMessageVO> messages = new ArrayList<>();
    /**
     * 原生插话收据及应用状态。
     */
    private List<ChatInputVO> inputs = new ArrayList<>();
}
