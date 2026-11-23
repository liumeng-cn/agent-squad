package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.bo.KeyValueBO;
import com.agentsquad.squad.v2.config.JsonbTypeHandler;
import com.agentsquad.squad.v2.enums.McpTransport;
import com.agentsquad.squad.v2.enums.VersionStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * MCP 连接版本
 * <p>
 * 映射 sq_mcp_server_version，保存一个可独立发布的连接配置；凭据只保存引用名。
 */
@Data
@TableName(value = "sq_mcp_server_version", schema = "squad_chat", autoResultMap = true)
public class McpServerVersionPO {
    /**
     * 存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版本业务ID
     */
    private Long mcpServerVersionId;

    /**
     * 服务业务ID
     */
    private Long mcpServerId;

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
     * 传输方式
     */
    private McpTransport transportType;

    /**
     * 远程连接地址
     */
    private String endpoint;

    /**
     * 凭据引用名
     */
    private String credentialRef;

    /**
     * 附加请求头
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<KeyValueBO> requestHeaders;

    /**
     * 连接与调用超时毫秒数
     */
    private Integer timeoutMs;

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
