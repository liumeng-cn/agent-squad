package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 聊天模型思考展示块
 * <p>
 * 用于聊天接口公开传输，不暴露原始 SDK 状态和执行载荷。
 */
@Data
public class ChatThinkingVO {
    /**
     * 原生模型调用标识。
     */
    private String modelCallId;
    /**
     * 供应商明确返回的公开思考文本。
     */
    private String content = "";
}
