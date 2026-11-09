package com.agentsquad.squad.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 网关认证与持久会话配置。
 *
 * <p>所有时间窗口和限流阈值可按环境覆盖；生产 Cookie 必须显式开启 Secure。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agentsquad.auth")
public class AuthProperties {

    /** BCrypt 密码散列的计算强度 */
    private int bcryptStrength = 10;
    /** 会话允许的最长空闲时长 */
    private Duration idleTimeout = Duration.ofHours(24);
    /** 会话从签发起的最长有效时长 */
    private Duration absoluteTimeout = Duration.ofDays(7);
    /** 更新会话活跃时间的最小间隔 */
    private Duration touchInterval = Duration.ofMinutes(5);
    /** 统计登录失败次数的时间窗口 */
    private Duration failureWindow = Duration.ofMinutes(15);
    /** 窗口内触发登录锁定的失败次数 */
    private int maxFailures = 5;
    /** 超过失败阈值后的登录锁定时长 */
    private Duration lockDuration = Duration.ofMinutes(15);
    /** 是否仅通过 HTTPS 发送会话 Cookie */
    private boolean secureCookie = false;
}
