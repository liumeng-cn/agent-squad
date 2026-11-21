package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 子Agent委派请求
 * <p>
 * 描述管理端提交的配置或操作条件。
 */
@Data
public class AgentDelegationDTO {
    /**
     * 子 Agent 版本ID
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String subagentVersionId;

    /**
     * 委派名称
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String delegationName;

    /**
     * 委派描述
     */
    @Size(max = 500)
    private String delegationDescription;
}
