package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 停止指定执行的请求
 * <p>
 * 用于聊天接口公开传输，不暴露原始 SDK 状态和执行载荷。
 */
@Data
public class ChatInterruptDTO {
    /**
     * 对话标识。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String convId;

    /**
     * 当前执行标识，防止停止后来的执行。
     */
    @NotBlank
    @Size(max = 200)
    private String runId;
}
