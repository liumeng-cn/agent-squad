package com.agentsquad.squad.config.properties;

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
    /** 是否启用当前配置项 */
    private boolean enabled;
    /** Redis 唤醒键使用的命名空间 */
    private String namespace = "squad:local";
    /** 唤醒队列允许保留的条目上限 */
    private int capacity = 10000;
    /** 重新领取未确认消息前的最小空闲时间，单位毫秒 */
    private long reclaimIdleMs = 90000;
}
