package com.agentsquad.squad.user.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动入口，所属 user-server 模块
 * 当前仅提供服务骨架，账号与用户管理业务待设计后补充
 */
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}