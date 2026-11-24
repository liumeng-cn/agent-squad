package com.agentsquad.squad.v2.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Agent 配置版本的工具关联
 * <p>
 * 映射 sq_agent_version_tool，按工具业务 ID 关联，配置态默认使用工具最新配置版本。
 */
@Data
@TableName(value = "sq_agent_version_tool", schema = "squad_chat")
public class AgentVersionToolPO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属 Agent 配置版本ID
     */
    private Long agentVersionId;

    /**
     * 关联的工具业务ID
     */
    private Long toolId;

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
