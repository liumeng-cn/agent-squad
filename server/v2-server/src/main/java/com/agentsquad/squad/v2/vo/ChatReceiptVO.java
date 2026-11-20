package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ChatDisposition;
import lombok.Data;

/**
 * 聊天输入接收收据
 * <p>
 * 用于聊天接口公开传输，不暴露原始 SDK 状态和执行载荷。
 */
@Data
public class ChatReceiptVO {
    /**
     * 业务对话ID。
     */
    private String convId;

    /**
     * 对话关联的原生会话ID。
     */
    private String sessionId;

    /**
     * 本次用户交互标识。
     */
    private String interactionId;

    /**
     * 实际归属的原生任务轮次。
     */
    private String turnId;

    /**
     * 原生提交命令或插话输入标识。
     */
    private String inputId;

    /**
     * 输入开启新轮次还是补充已有轮次。
     */
    private ChatDisposition disposition;
}
