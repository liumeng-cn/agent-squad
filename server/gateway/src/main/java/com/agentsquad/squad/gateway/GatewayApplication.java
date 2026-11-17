package com.agentsquad.squad.gateway;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动入口，所属 gateway 模块
 * 当前仅保留基础架构，认证与账号业务待用户服务设计后迁入
 */
@MapperScan(basePackages = "com.agentsquad.squad.mapper", annotationClass = Mapper.class)
@SpringBootApplication(scanBasePackages = "com.agentsquad.squad")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
