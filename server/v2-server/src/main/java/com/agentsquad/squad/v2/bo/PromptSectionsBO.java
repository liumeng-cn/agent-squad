package com.agentsquad.squad.v2.bo;

import lombok.Data;

/**
 * 结构化提示词分块
 * <p>
 * 按业界标准五段式结构保存 Agent 提示词，便于清晰维护及后续接入 AI 辅助填写与局部润色。
 */
@Data
public class PromptSectionsBO {
    /**
     * 角色与定位
     */
    private String role = "";

    /**
     * 核心目标
     */
    private String goal = "";

    /**
     * 工作流程与策略
     */
    private String workflow = "";

    /**
     * 约束与边界
     */
    private String constraints = "";

    /**
     * 输出规范
     */
    private String outputFormat = "";
}
