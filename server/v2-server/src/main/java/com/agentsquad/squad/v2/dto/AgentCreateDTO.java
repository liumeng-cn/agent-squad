package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.AgentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 建档请求
 * <p>
 * 同一事务创建 Agent 目录与第一个版本草稿，类型创建后不可修改。
 */
@Data
public class AgentCreateDTO {
    /**
     * Agent 编码，创建后不可修改
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String agentCode;

    /**
     * 展示名称
     */
    @NotBlank
    @Size(max = 120)
    private String agentName;

    /**
     * 主 Agent 或子 Agent
     */
    @NotNull
    private AgentType agentType;

    /**
     * 第一个版本草稿
     */
    @NotNull
    @Valid
    private AgentDraftDTO draft;
}
