package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.McpTransport;
import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * MCP 服务版本视图
 * <p>
 * 返回一个连接版本的完整配置，请求头保留占位符，不包含凭据值。
 */
@Data
public class McpServerVersionVO {
    /**
     * 版本业务ID
     */
    private String mcpServerVersionId;

    /**
     * 服务业务ID
     */
    private String mcpServerId;

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
     * 最后更新时间
     */
    private Instant lastUpdateTime;

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
    private List<KeyValueVO> headers;

    /**
     * 连接与调用超时毫秒数
     */
    private Integer timeoutMs;
}
