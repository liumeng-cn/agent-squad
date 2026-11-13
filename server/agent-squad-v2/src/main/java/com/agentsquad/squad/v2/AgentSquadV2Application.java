package com.agentsquad.squad.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Squad 聊天应用
 * <p>
 * 提供基于原生 AgentSession 的持续对话与实时执行过程。
 */
@SpringBootApplication
public class AgentSquadV2Application {
    public static void main(String[] args) {
        SpringApplication.run(AgentSquadV2Application.class, args);
    }
}
