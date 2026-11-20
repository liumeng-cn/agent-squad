package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户交互答复
 * <p>
 * 绑定对话、轮次和原生待答标识，只提交用户答案，不接受客户端指定工具名称或结果类型。
 */
@Data
public class ChatAnswerDTO {
    /**
     * 对话标识。
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String convId;

    /**
     * 待答事项所属轮次。
     */
    @NotBlank
    @Size(max = 200)
    private String turnId;

    /**
     * 原生日志中的待答标识。
     */
    @NotBlank
    @Size(max = 200)
    private String requestId;

    /**
     * 用户选择或输入的答案。
     */
    @NotBlank
    @Size(max = 4000)
    private String answer;
}
