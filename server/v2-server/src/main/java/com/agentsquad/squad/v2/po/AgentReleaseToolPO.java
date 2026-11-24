package com.agentsquad.squad.v2.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Master 发布定格的工具与 MCP 快照
 * <p>
 * 映射 sq_agent_release_tool，记录发布时刻 Master 及其所有 Sub 所绑定工具与 MCP 的确切配置版本。
 */
@Data
@TableName(value = "sq_agent_release_tool", schema = "squad_chat")
public class AgentReleaseToolPO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属发布快照业务ID
     */
    private Long releaseId;

    /**
     * 工具归属的 Agent 业务ID（Master 或 Sub）
     */
    private Long ownerAgentId;

    /**
     * 工具归属的 Agent 配置版本ID
     */
    private Long ownerAgentVersionId;

    /**
     * 定格的工具业务ID
     */
    private Long toolId;

    /**
     * 定格的工具配置版本ID
     */
    private Long toolVersionId;

    /**
     * 发布时定格的工具名称
     */
    private String toolName;

    /**
     * 发布时定格的工具前端展示别称
     */
    private String toolDisplayAlias;

    /**
     * 若为 MCP 工具，定格的 MCP 服务业务ID
     */
    private Long mcpServerId;

    /**
     * 若为 MCP 工具，定格的 MCP 配置版本ID
     */
    private Long mcpServerVersionId;

    /**
     * 发布时定格的 MCP 服务名称
     */
    private String mcpServerName;

    /**
     * 展示顺序
     */
    private Integer sortNo;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;
}
