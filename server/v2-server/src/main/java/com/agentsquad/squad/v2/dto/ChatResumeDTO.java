package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 继续原生任务轮次
 * <p>
 * 只请求继续指定轮次；存在未确认工具结果或待答交互时由 SDK 拒绝，不重放外部操作。
 */
@Data
public class ChatResumeDTO {
    /**
     * 会话标识。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String sessionId;

    /**
     * 需要继续的原生轮次。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String turnId;
}
