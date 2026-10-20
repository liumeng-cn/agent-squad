package com.agentsquad.squad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Squad 服务启动入口。
 *
 * <p>启动时验证并迁移业务表；后台运行调度由运行模块逐步接入。</p>
 */
@SpringBootApplication
@EnableScheduling
public class AgentSquadApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentSquadApplication.class, args);
    }
}
