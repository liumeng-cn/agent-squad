package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 聊天输入
 * <p>
 * 会话标识由页面创建并在连续对话中复用，请求标识对应原生提交幂等键。
 */
@Data
public class ChatMessageDTO {
    /**
     * 当前页面会话标识。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String sessionId;

    /**
     * 本次发送标识，同一次提交重试时保持一致。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String requestId;

    /**
     * 用户输入的正文。
     */
    @NotBlank
    @Size(max = 20000)
    private String message;
}
