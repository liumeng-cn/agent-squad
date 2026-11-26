package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 聊天输入
 * <p>
 * 对话标识由客户端生成并跨轮次复用，首次发送时服务端按该标识幂等建档；
 * 交互标识只标识本次发送，目标轮次用于插话准入。
 */
@Data
public class ChatMessageDTO {
    /**
     * 当前页面对话标识；首次发送时作为建档幂等键，网络重试沿用同一标识。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String convId;

    /**
     * 首次建档时指定绑定的已发布 Master 编码；为空时自动选取默认可用 Master，已有对话忽略该字段。
     */
    @Size(max = 64)
    private String agentCode;

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
