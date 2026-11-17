package com.agentsquad.squad.user.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动入口，所属 user-server 模块
 * 提供手机号注册、用户资料及跨服务账号查询，数据库访问统一经过Mapper
 */
@org.springframework.boot.context.properties.EnableConfigurationProperties(com.agentsquad.squad.user.server.config.properties.UserProperties.class)
@SpringBootApplication
@org.mybatis.spring.annotation.MapperScan("com.agentsquad.squad.user.server.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
