package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ChatInputStatus;
import lombok.Data;

import java.util.List;

/**
 * 会话补充要求
 * <p>
 * 用于聊天接口公开传输，不暴露原始 SDK 状态和执行载荷。
 */
@Data
public class ChatInputVO {
    /**
     * 原生输入命令标识。
     */
    private String id;
    /**
     * 实际归属轮次。
     */
    private String turnId;
    /**
     * 用户补充的公开正文。
     */
    private String content;
    /**
     * 是否已经应用至执行上下文。
     */
    private ChatInputStatus status;

    /**
     * 原生输入包含的消息标识，用于将处理状态关联到用户气泡。
     */
    private List<String> messageIds;
}
