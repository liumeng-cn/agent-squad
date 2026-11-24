package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.AgentType;
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
 * Agent 保存请求
 * <p>
 * 新增与编辑统一提交，保存时不阻断未填完的提示词或职责描述，但所选工具和子 Agent 必须处于启用且就绪状态。
 */
@Data
public class AgentSaveDTO {
    /**
     * 编辑已有 Agent 时的预期目录修订号，新增时为空
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 后台展示名称
     */
    @NotBlank
    @Size(max = 120)
    private String agentName;

    /**
     * 聊天前端事件展示别称，留空回退 Agent 名称
     */
    @Size(max = 60)
    private String displayAlias = "";

    /**
     * 主 Agent 或子 Agent，仅新增时生效，创建后不可修改
     */
    private AgentType agentType;

    /**
     * 职责描述，子 Agent 就绪与发布前必填
     */
    @Size(max = 500)
    private String description = "";

    /**
     * 结构化提示词分块
     */
    @Valid
    private PromptSectionsDTO promptSections = new PromptSectionsDTO();

    /**
     * 最大执行步数，空值使用平台默认值
     */
    @Min(1)
    @Max(200)
    private Integer maxIters;

    /**
     * 关联的工具业务ID列表
     */
    @NotNull
    @Size(max = 100)
    private List<@NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String> toolIds = List.of();

    /**
     * 子 Agent 委派列表，仅 Master 可配置
     */
    @NotNull
    @Valid
    @Size(max = 50)
    private List<@NotNull AgentDelegationDTO> subagents = List.of();
}
