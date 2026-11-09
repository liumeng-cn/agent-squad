package com.agentsquad.squad.config;


import com.agentsquad.squad.config.properties.AuthProperties;
import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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

    @Resource
    private Environment environment;

    /**
     * 生产认证参数被外部覆盖时拒绝启动，避免降级到开发账号或非安全 Cookie。
     */
    @PostConstruct
    public void validateProductionAuthentication() {
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && (!authProperties.isSecureCookie()
                || !environment.getProperty("agentsquad.identity.strict", Boolean.class, false)
                || !"never".equals(environment.getProperty("spring.sql.init.mode")))) {
            throw new IllegalStateException("生产网关必须使用数据库认证、安全 Cookie、严格内部签名并禁止启动 DDL");
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(authProperties.getBcryptStrength());
    }
}
