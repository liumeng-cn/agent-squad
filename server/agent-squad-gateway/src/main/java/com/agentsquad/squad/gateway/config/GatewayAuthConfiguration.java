package com.agentsquad.squad.gateway.config;

import com.agentsquad.squad.gateway.auth.AuthProperties;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 网关认证基础设施配置。
 *
 * <p>密码统一使用自带盐的 BCrypt；会话清理依赖 Spring 调度线程，不占用 WebFlux event-loop。</p>
 */
@Configuration
@EnableScheduling
public class GatewayAuthConfiguration {

    @Resource
    private AuthProperties authProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(authProperties.getBcryptStrength());
    }
}
