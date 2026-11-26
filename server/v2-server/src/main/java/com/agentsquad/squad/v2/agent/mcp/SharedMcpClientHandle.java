package com.agentsquad.squad.v2.agent.mcp;

import com.agentsquad.squad.v2.bo.McpConnectionBO;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 发布快照级共享 MCP 连接句柄
 * <p>
 * 同一 Master Release 内指向同一份 MCP 配置版本的多个工具共享同一句柄；
 * 采用首次调用时懒加载连接，避免应用启动或打开历史会话时因外部 MCP 瞬时不可达而阻塞失败。
 */
@Slf4j
public class SharedMcpClientHandle implements AutoCloseable {
    private final McpClientFactory clientFactory;

    private final McpConnectionBO connection;

    private McpClientWrapper client;

    private boolean closed;

    public SharedMcpClientHandle(McpClientFactory clientFactory, McpConnectionBO connection) {
        this.clientFactory = clientFactory;
        this.connection = connection;
    }

    public McpConnectionBO getConnection() {
        return connection;
    }

    /**
     * 获取已初始化的 MCP 客户端连接，尚未建立时按需建连。
     */
    public synchronized McpClientWrapper acquire() {
        if (closed) {
            throw new IllegalStateException("当前发布快照关联的 MCP 连接已释放");
        }
        if (client == null || !client.isInitialized()) {
            closeQuietly(client);
            client = clientFactory.connect(connection);
        }
        return client;
    }

    /**
     * 调用发生连接级故障时清理失效客户端，便于下次调用自动重连。
     */
    public synchronized void invalidate(McpClientWrapper broken) {
        if (client != null && client == broken) {
            closeQuietly(client);
            client = null;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeQuietly(client);
        client = null;
    }

    private void closeQuietly(McpClientWrapper target) {
        if (target == null) {
            return;
        }
        try {
            target.close();
        } catch (RuntimeException ignored) {
            log.debug("关闭 MCP 连接句柄出现预期内异常，client={}", connection.getClientName());
        }
    }
}
