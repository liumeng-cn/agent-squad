package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ChatEventType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 聊天公开事件
 * <p>
 * 服务端自行维护的输出契约，仅包含页面所需字段，不暴露 SDK 输入、原始载荷及工具结果正文。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEventVO {
    /**
     * 公开事件类型
     */
    private ChatEventType type;

    /**
     * 页面会话标识
     */
    private String threadId;

    /**
     * 本轮请求标识
     */
    private String runId;

    /**
     * 事件发生时间，SDK 未提供时为空
     */
    private Long timestamp;

    /**
     * 模型消息标识
     */
    private String messageId;

    /**
     * 正文或思考增量
     */
    private String delta;

    /**
     * 工具调用标识
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolCallName;

    /**
     * 面向用户的结果说明
     */
    private String message;

    /**
     * 本轮是否暂停等待交互
     */
    private boolean waiting;

}
