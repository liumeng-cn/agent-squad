package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 结构化提示词分块请求
 * <p>
 * 保存时不强制必填，发布和就绪度校验要求角色与定位、核心目标非空。
 */
@Data
public class PromptSectionsDTO {
    /**
     * 角色与定位
     */
    @Size(max = 4000)
    private String role = "";

    /**
     * 核心目标
     */
    @Size(max = 4000)
    private String goal = "";

    /**
     * 工作流程与策略
     */
    @Size(max = 6000)
    private String workflow = "";

    /**
     * 约束与边界
     */
    @Size(max = 4000)
    private String constraints = "";

    /**
     * 输出规范
     */
    @Size(max = 2000)
    private String outputFormat = "";
}
