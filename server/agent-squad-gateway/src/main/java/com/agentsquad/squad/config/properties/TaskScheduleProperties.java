package com.agentsquad.squad.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 后台调度配置属性
 *
 * <p>配置认证会话清理的间隔和启动延迟，时间单位为毫秒。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agentsquad.scheduler")
public class TaskScheduleProperties {

    /** 认证会话清理间隔，单位毫秒 */
    private volatile long authCleanupDelayMs = 600000;

    /** 启动到首次认证会话清理的等待时间，单位毫秒 */
    private volatile long authCleanupInitialDelayMs = 60000;

}
