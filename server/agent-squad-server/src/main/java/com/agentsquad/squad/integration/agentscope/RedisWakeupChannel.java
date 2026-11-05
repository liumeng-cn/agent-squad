package com.agentsquad.squad.integration.agentscope;

/**
 * 持久执行工作的就绪通道
 * <p>
 * 命令、控制和原生投影独立消费，避免相互占用就绪窗口。
 */
public enum RedisWakeupChannel {
    COMMAND,
    CONTROL,
    PROJECTION
}
