package com.agentsquad.squad.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis执行唤醒配置
 * <p>
 * 仅控制可重建的就绪引用；数据库仍负责命令身份、授权、租约和执行结果。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agentsquad.redis-wakeup")
public class RedisWakeupProperties {
    private boolean enabled;
    private String namespace = "squad:local";
    private int capacity = 10000;
    private long reclaimIdleMs = 90000;
}
