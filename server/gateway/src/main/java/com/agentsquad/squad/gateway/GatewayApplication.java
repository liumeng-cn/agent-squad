package com.agentsquad.squad.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动入口，所属 gateway 模块
 * 提供服务路由、JWT登录、Redis会话和接口准入，用户持久化由user-server维护
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
