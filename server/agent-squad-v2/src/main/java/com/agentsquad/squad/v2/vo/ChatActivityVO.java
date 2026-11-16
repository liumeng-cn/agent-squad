package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ChatExecutionStatus;
import lombok.Data;

/**
 * 聊天公开工具过程
 * <p>
 * 用于聊天接口公开传输，不暴露原始 SDK 状态和执行载荷。
 */
@Data
public class ChatActivityVO {
    /**
     * 工具调用标识。
     */
    private String id;
    /**
     * 工具名称。
     */
    private String title;
    /**
     * 执行状态。
     */
    private ChatExecutionStatus status = ChatExecutionStatus.RUNNING;
    /**
     * 开始时间。
     */
    private long startedAt;

    /**
     * 原生执行结束时间；仍在执行或尚未确认结束时为空。
     */
    private Long finishedAt;
}
