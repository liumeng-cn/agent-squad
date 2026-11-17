package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ChatExecutionStatus;
import com.agentsquad.squad.v2.enums.ChatMessageRole;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天公开历史消息
 * <p>
 * 用于聊天接口公开传输，不暴露原始 SDK 状态和执行载荷。
 */
@Data
public class ChatHistoryMessageVO {
    /**
     * 用户消息或模型回复段的稳定标识。
     */
    private String id;
    /**
     * 原生任务轮次。
     */
    private String turnId;
    /**
     * 公开消息角色。
     */
    private ChatMessageRole role;
    /**
     * 正文，不包含提示词和原始工具结果。
     */
    private String content = "";
    /**
     * 回复执行状态。
     */
    private ChatExecutionStatus status = ChatExecutionStatus.RUNNING;
    /**
     * 消息开始时间。
     */
    private long startedAt;

    /**
     * 回复段结束时间，以原生事件时间为准；未确认结束时为空。
     */
    private Long finishedAt;
    /**
     * 公开工具状态。
     */
    private List<ChatActivityVO> activities = new ArrayList<>();
    /**
     * 模型明确返回的思考块，不含加密元数据。
     */
    private List<ChatThinkingVO> thinkingBlocks = new ArrayList<>();
}
