package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.config.JsonbTypeHandler;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ToolType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具配置版本历史
 * <p>
 * 映射 sq_tool_version，每次保存新增一条不可变配置版本；MCP 工具按 MCP 业务 ID 关联。
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
     * 配置版本号
     */
    private Integer versionNo;

    /**
     * 版本就绪状态
     */
    private ConfigReadiness readiness;

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
     * 关联的 MCP 服务业务ID
     */
    private Long mcpServerId;

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
     * 创建时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant createTime;

    /**
     * 最后更新时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant lastUpdateTime;
}
