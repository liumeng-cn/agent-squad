package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import com.agentsquad.squad.v2.enums.ToolSource;
import lombok.Data;

/**
 * 工具目录视图
 * <p>
 * 用于管理端查看资源目录。
 */
@Data
public class ToolVO {
    /**
     * 资源业务ID
     */
    private String toolId;

    /**
     * 工具编码
     */
    private String toolCode;

    /**
     * 展示名称
     */
    private String toolName;

    /**
     * 工具来源
     */
    private ToolSource toolType;

    /**
     * 启停状态
     */
    private AgentConfigStatus status;
}
