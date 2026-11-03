package com.agentsquad.squad.bootstrap;

import com.agentsquad.squad.gateway.config.GatewayAuthConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 初始管理员命令的精简上下文。
 *
 * <p>只装配账号、认证与持久层，不加载网关路由和 Web 服务器；与网关应用分处不同扫描根，
 * 避免自动配置排除项影响正常网关启动。</p>
 */
@SpringBootApplication(
        scanBasePackages = {"com.agentsquad.squad.gateway.admin", "com.agentsquad.squad.gateway.auth"},
        exclude = {GatewayAutoConfiguration.class, GatewayMetricsAutoConfiguration.class})
@MapperScan({"com.agentsquad.squad.gateway.admin", "com.agentsquad.squad.gateway.auth"})
@Import(GatewayAuthConfiguration.class)
public class BootstrapAdminApplication {
}
