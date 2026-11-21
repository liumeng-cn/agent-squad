package com.agentsquad.squad.v2.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

import lombok.Data;

/**
 * Agent草稿请求
 * <p>
 * 描述管理端提交的配置或操作条件。
 */
@Data
public class AgentDraftDTO {
    /**
     * 编辑现有草稿时提供的修订号
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 业务提示词
     */
    @NotBlank
    @Size(max = 20000)
    private String systemPrompt;

    /**
     * 职责描述
     */
    @Size(max = 500)
    private String description = "";

    /**
     * 最大步骤
     */
    @Positive
    private Integer maxIters;

    /**
     * 授权工具版本
     */
    @NotNull
    @Size(max = 100)
    private List<@NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String> toolVersionIds = List.of();

    /**
     * 委派绑定
     */
    @NotNull
    @Valid
    @Size(max = 100)
    private List<@NotNull AgentDelegationDTO> subagents = List.of();
}
