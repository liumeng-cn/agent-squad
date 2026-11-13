package com.agentsquad.squad.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Agent Squad V2 服务启动入口。 */
@EnableScheduling
@SpringBootApplication
public class AgentSquadV2Application {
    public static void main(String[] args) {
        SpringApplication.run(AgentSquadV2Application.class, args);
    }
}
