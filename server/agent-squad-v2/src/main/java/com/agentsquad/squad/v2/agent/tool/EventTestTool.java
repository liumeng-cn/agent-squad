package com.agentsquad.squad.v2.agent.tool;

import io.agentscope.core.tool.Tool;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 聊天事件测试工具
 * <p>
 * 通过固定延迟产生可观察的原生工具生命周期，不访问外部系统、不修改业务数据。
 */
public class EventTestTool {

    /**
     * 等待三秒后返回固定测试结果；由框架产生工具开始和完成事件。
     * 使用非阻塞延迟，不自行发布或伪造聊天事件。
     */
    @Tool(name = "test_event_tool", description = "测试工具执行事件。用户要求验证工具事件或调用测试工具时使用，等待三秒后返回固定测试结果。", readOnly = true)
    public Mono<String> execute() {
        return Mono.delay(Duration.ofSeconds(3))
                .map(ignored -> "测试工具执行完成：已等待三秒，工具调用事件链路正常。");
    }
}
