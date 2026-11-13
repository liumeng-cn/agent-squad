package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试工具原生事件验收
 * <p>
 * 使用可控模型真实执行工具，验证官方 AG-UI 工具调用及结果事件。
 */
class EventTestToolTest {
    @TempDir
    Path workspace;

    @Test
    void executesToolAndProjectsRunningAndCompletedEvents() {
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("tool-test");
        AtomicBoolean first = new AtomicBoolean(true);
        when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
            if (first.getAndSet(false)) {
                return Flux.just(ChatResponse.builder().content(List.of(ToolUseBlock.builder()
                                .id("test-call").name("test_event_tool").input(Map.of()).build()))
                        .finishReason("tool_calls").build());
            }
            return Flux.just(ChatResponse.builder().content(List.of(TextBlock.builder()
                    .text("工具验证完成").build())).finishReason("stop").build());
        });
        var toolkit = new Toolkit();
        toolkit.registerTool(new EventTestTool());
        try (var agent = HarnessAgent.builder().agentId("tool-test").name("tool-test")
                .model(model).toolkit(toolkit).workspace(workspace).build()) {
            var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
            beans.registerSingleton("agent", agent);
            var service = new com.agentsquad.squad.v2.service.ChatService();
            ReflectionTestUtils.setField(service, "agentProvider", beans.getBeanProvider(HarnessAgent.class));
            var request = new ChatMessageDTO();
            request.setSessionId("tool-session");
            request.setRequestId("tool-request");
            request.setMessage("调用测试工具");
            var events = service.sendAgui(request, "1").collectList().block(Duration.ofSeconds(20));
            assertThat(events).anyMatch(event -> event instanceof io.agentscope.core.agui.event.AguiEvent.ToolCallStart);
            assertThat(events).anyMatch(event -> event instanceof io.agentscope.core.agui.event.AguiEvent.ToolCallResult);
            assertThat(events.getLast()).isInstanceOf(io.agentscope.core.agui.event.AguiEvent.RunFinished.class);
        }
    }
}
