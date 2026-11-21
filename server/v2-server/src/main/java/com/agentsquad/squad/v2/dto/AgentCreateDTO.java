package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent建档请求
 * <p>
 * 描述管理端提交的配置或操作条件。
 */
@Data
public class AgentCreateDTO {
    /**
     * 能力编码
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
     * 主子类型
     */
    @NotNull
    private AgentType agentType;
}
