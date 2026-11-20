package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 聊天输入
 * <p>
 * 会话标识跨轮次复用，交互标识只标识本次发送；目标轮次用于插话准入。
 */
@Data
public class ChatMessageDTO {
    /**
     * 当前页面会话标识。
     */
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String sessionId;

    /**
     * 已建档的对话ID，首次发送时为空
     */
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String convId;

    /**
     * 本次用户交互标识；新任务重试保持一致，插话暂不承诺网络重试幂等。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String interactionId;

    /**
     * 页面观察到的任务轮次；为空时直接提交新任务。
     */
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String observedTurnId;

    /**
     * 用户输入的正文。
     */
    @NotBlank
    @Size(max = 20000)
    private String message;
}
