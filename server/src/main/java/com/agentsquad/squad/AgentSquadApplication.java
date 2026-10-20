package com.agentsquad.squad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Squad 服务启动入口。
 *
 * <p>D0 仅提供应用容器与框架兼容性验证入口，真正的运行调度由后续切片逐步接入。</p>
 */
@SpringBootApplication
public class AgentSquadApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentSquadApplication.class, args);
    }
}
