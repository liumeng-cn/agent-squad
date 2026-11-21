package com.agentsquad.squad.gateway;

import com.agentsquad.squad.gateway.config.UserFeignConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 网关服务启动入口，所属 gateway 模块
 * 提供服务路由、JWT登录、Redis会话和接口准入，用户持久化由user-server维护
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.agentsquad.squad.user.api.client", defaultConfiguration = UserFeignConfiguration.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
