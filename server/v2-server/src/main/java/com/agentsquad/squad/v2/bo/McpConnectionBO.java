package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.McpTransport;
import lombok.Data;

import java.util.Map;

/**
 * MCP 连接参数
 * <p>
 * 由已校验的服务版本和凭据生成，请求头中的凭据占位符已替换为真实值，不得输出到日志。
 */
@Data
public class McpConnectionBO {
    /**
     * 客户端名称，用于 SDK 日志和工具来源
     */
    private String clientName;

    /**
     * 传输方式
     */
    private McpTransport transportType;

    /**
     * 远程连接地址
     */
    private String endpoint;

    /**
     * 已替换凭据的请求头
     */
    private Map<String, String> headers;

    /**
     * 连接与调用超时毫秒数
     */
    private Integer timeoutMs;

    /**
     * 凭据值，用于遮蔽错误信息
     */
    private String credential;
}
