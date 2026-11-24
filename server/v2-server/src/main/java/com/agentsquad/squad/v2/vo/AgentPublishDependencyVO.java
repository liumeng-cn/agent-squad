package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 发布预检依赖项视图
 */
@Data
public class AgentPublishDependencyVO {
    /**
     * 资源类型：MASTER / SUB_AGENT / TOOL / MCP
     */
    private String resourceKind;

    /**
     * 资源业务ID
     */
    private String resourceId;

    /**
     * 资源名称
     */
    private String resourceName;

    /**
     * 即将定格的配置版本号
     */
    private Integer versionNo;

    /**
     * 展示别称
     */
    private String displayAlias;

    /**
     * 归属说明（如直接绑定或某个 Sub 下）
     */
    private String scope;
}
