package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 子 Agent 委派请求
 * <p>
 * 主 Agent 配置中的一项委派，按子 Agent 业务 ID 关联，默认指向其最新可用配置版本。
 */
@Data
public class AgentDelegationDTO {
    /**
     * 子 Agent 业务ID
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String subagentId;

    /**
     * 主 Agent 内唯一的委派名称
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String delegationName;

    /**
     * 在当前主 Agent 下的委派说明，留空使用子 Agent 职责描述
     */
    @Size(max = 500)
    private String delegationDescription;

    /**
     * 委派展示别称，留空回退子 Agent 展示别称或名称
     */
    @Size(max = 60)
    private String displayAlias = "";
}
