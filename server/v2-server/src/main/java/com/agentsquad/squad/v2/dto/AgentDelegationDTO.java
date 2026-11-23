package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 子 Agent 委派请求
 * <p>
 * 主 Agent 版本中的一项委派，绑定子 Agent 的固定已发布版本。
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
     * 主 Agent 内唯一的委派名称
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String delegationName;

    /**
     * 在当前主 Agent 下的委派说明
     */
    @Size(max = 500)
    private String delegationDescription;
}
