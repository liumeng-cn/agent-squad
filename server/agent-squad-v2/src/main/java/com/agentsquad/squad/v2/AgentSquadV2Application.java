package com.agentsquad.squad.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Squad V2 服务启动入口。
 *
 * <p>独立部署的应用模块，后续业务能力在此模块内按职责分包接入。</p>
 */
@SpringBootApplication
public class AgentSquadV2Application {

    public static void main(String[] args) {
        SpringApplication.run(AgentSquadV2Application.class, args);
    }
}
