package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.config.JsonbTypeHandler;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.enums.VersionStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具版本
 * <p>
 * 映射 sq_tool_version，保存模型可见定义和按类型的实现配置；发布后冻结，Agent 按版本ID固定绑定。
 */
@Data
@TableName(value = "sq_tool_version", schema = "squad_chat", autoResultMap = true)
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
     * 工具业务ID
     */
    private Long toolId;

    /**
     * 工具类型，与目录一致
     */
    private ToolType toolType;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private VersionStatus status;

    /**
     * 版本修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 模型工具名称
     */
    private String exposedName;

    /**
     * 模型可见的用途说明
     */
    private String description;

    /**
     * 调用参数的 JSON Schema
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> inputSchema;

    /**
     * 是否只读
     */
    private Boolean readOnly;

    /**
     * 内置工具实现标识
     */
    private String implementationKey;

    /**
     * MCP 服务版本ID
     */
    private Long mcpServerVersionId;

    /**
     * MCP 服务中的工具名称
     */
    private String remoteToolName;

    /**
     * HTTP 请求模板
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private HttpToolSpecBO httpSpec;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 最后更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 最后更新时间，由数据库维护
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant lastUpdateTime;
}
