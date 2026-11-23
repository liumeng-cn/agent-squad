package com.agentsquad.squad.v2.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Agent 草稿请求
 * <p>
 * 保存草稿只校验格式与绑定可用性，提示词和职责描述的必填规则在发布时校验。
 */
@Data
public class AgentDraftDTO {
    /**
     * 编辑已有草稿时的预期修订号
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 职责描述，子 Agent 发布前必填
     */
    @NotNull
    @Size(max = 500)
    private String description = "";

    /**
     * 系统提示词，发布前必填
     */
    @NotNull
    @Size(max = 20000)
    private String systemPrompt = "";

    /**
     * 最大执行步数，空值使用平台默认值
     */
    @Min(1)
    @Max(200)
    private Integer maxIters;

    /**
     * 绑定的工具版本ID
     */
    @NotNull
    @Size(max = 100)
    private List<@NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String> toolVersionIds = List.of();

    /**
     * 子 Agent 委派，仅主 Agent 可配置
     */
    @NotNull
    @Valid
    @Size(max = 50)
    private List<@NotNull AgentDelegationDTO> subagents = List.of();
}
