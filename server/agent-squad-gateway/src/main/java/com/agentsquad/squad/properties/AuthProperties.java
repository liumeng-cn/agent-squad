package com.agentsquad.squad.properties;

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

    private int bcryptStrength = 10;
    private Duration idleTimeout = Duration.ofHours(24);
    private Duration absoluteTimeout = Duration.ofDays(7);
    private Duration touchInterval = Duration.ofMinutes(5);
    private Duration failureWindow = Duration.ofMinutes(15);
    private int maxFailures = 5;
    private Duration lockDuration = Duration.ofMinutes(15);
    private boolean secureCookie = false;
}
