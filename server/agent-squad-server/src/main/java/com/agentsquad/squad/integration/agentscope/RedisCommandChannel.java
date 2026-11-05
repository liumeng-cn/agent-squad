package com.agentsquad.squad.integration.agentscope;

/**
 * 持久命令的就绪通道
 * <p>
 * 停止与答复独立消费，避免被普通模型提交积压占用就绪窗口。
 */
public enum RedisCommandChannel {
    COMMAND,
    CONTROL
}
