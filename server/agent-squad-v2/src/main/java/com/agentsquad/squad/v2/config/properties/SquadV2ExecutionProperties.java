package com.agentsquad.squad.v2.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * V2 执行与事件缓冲配置。
 */
@Data
@ConfigurationProperties(prefix = "agentsquad.v2.execution")
public class SquadV2ExecutionProperties {
    /**
     * 单次执行最多保留的事件数量。
     */
    private int eventBufferCapacity = 5000;
    /**
     * 已结束执行及事件缓冲的保留分钟数。
     */
    private long completedRetentionMinutes = 30;
    /**
     * local Profile 未经过网关时使用的本地测试用户 ID。
     */
    private long localUserId = 1L;
}