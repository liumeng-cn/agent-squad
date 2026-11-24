package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 结构化提示词分块视图
 */
@Data
public class PromptSectionsVO {
    private String role = "";

    private String goal = "";

    private String workflow = "";

    private String constraints = "";

    private String outputFormat = "";
}
