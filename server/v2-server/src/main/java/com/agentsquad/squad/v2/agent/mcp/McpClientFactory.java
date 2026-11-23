package com.agentsquad.squad.v2.agent.mcp;

import com.agentsquad.squad.v2.bo.McpConnectionBO;
import com.agentsquad.squad.v2.bo.McpRemoteToolBO;
import com.agentsquad.squad.v2.enums.McpTransport;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端工厂
 * <p>
 * 通过 AgentScope 公开的 McpClientBuilder 建立远程连接并完成初始化，调用方负责关闭返回的客户端。
 * 只支持远程 HTTP 传输，不在服务端启动本地进程。
 */
@Slf4j
@Component
public class McpClientFactory {
    /**
     * 建立并初始化连接
     * <p>
     * 初始化包含协议握手和工具列表缓存；失败时关闭已创建的客户端并抛出异常。
     */
    public McpClientWrapper connect(McpConnectionBO connection) {
        Duration timeout = Duration.ofMillis(connection.getTimeoutMs());
        McpClientBuilder builder = McpClientBuilder.create(connection.getClientName());
        if (connection.getTransportType() == McpTransport.SSE) {
            builder.sseTransport(connection.getEndpoint());
        } else {
            builder.streamableHttpTransport(connection.getEndpoint());
        }
        builder.headers(connection.getHeaders())
                .timeout(timeout)
                .initializationTimeout(timeout);

        McpClientWrapper client = builder.buildAsync().block(timeout);
        if (client == null) {
            throw new IllegalStateException("MCP 客户端创建失败");
        }
        try {
            client.initialize().block(timeout.plusSeconds(5));
            return client;
        } catch (RuntimeException failed) {
            client.close();
            throw failed;
        }
    }

    /**
     * 发现远程工具
     * <p>
     * 建立临时连接读取工具列表，结束后立即关闭连接；参数结构按 SDK 规则转换为 JSON Schema。
     */
    public List<McpRemoteToolBO> discover(McpConnectionBO connection) {
        Duration timeout = Duration.ofMillis(connection.getTimeoutMs());
        try (McpClientWrapper client = connect(connection)) {
            List<McpSchema.Tool> tools = client.listTools().block(timeout);
            List<McpRemoteToolBO> result = new ArrayList<>();
            if (tools == null) {
                return result;
            }

            for (McpSchema.Tool tool : tools) {
                result.add(toRemoteTool(tool));
            }
            log.info("MCP工具发现完成，client={}，工具数={}", connection.getClientName(), result.size());
            return result;
        }
    }

    private McpRemoteToolBO toRemoteTool(McpSchema.Tool tool) {
        Map<String, Object> schema = McpTool.convertMcpSchemaToParameters(tool.inputSchema(), null);
        boolean readOnly = tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().readOnlyHint());

        McpRemoteToolBO remote = new McpRemoteToolBO();
        remote.setName(tool.name());
        remote.setTitle(tool.title());
        remote.setDescription(tool.description() == null ? "" : tool.description());
        remote.setInputSchema(schema);
        remote.setReadOnly(readOnly);
        return remote;
    }
}
