package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import com.agentsquad.squad.v2.enums.ToolSource;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 工具版本
 * <p>
 * 保存可独立发布的工具或连接配置。
 */
@Data
@TableName(value = "sq_tool_version", schema = "squad_chat")
public class ToolVersionPO {
    /**
     * 存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版本业务ID
     */
    private Long toolVersionId;

    /**
     * 资源业务ID
     */
    private Long toolId;

    /**
     * 顺序版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private AgentVersionStatus status;

    /**
     * 版本修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 目录工具来源
     */
    private ToolSource toolType;

    /**
     * 模型工具名称
     */
    private String exposedName;

    /**
     * 原生或Java实现版本标识
     */
    private String implementationKey;

    /**
     * MCP连接版本ID
     */
    private Long mcpServerVersionId;

    /**
     * 远端工具名称
     */
    private String remoteToolName;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;
}
