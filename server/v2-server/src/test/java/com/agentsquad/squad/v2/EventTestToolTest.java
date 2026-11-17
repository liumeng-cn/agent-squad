package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * 使用可控模型真实执行工具，验证原生工具调用及公开状态投影。
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
            var sessions = AguiSessionTest.sessions(agent);
            var service = AguiSessionTest.service(sessions);
            var request = AguiSessionTest.input("tool-request", "调用测试工具");
            var receipt = service.send(request, "1");
            var session = sessions.open("conversation", "1");
            assertThat(Flux.interval(Duration.ofMillis(25))
                    .filter(tick -> sessions.read("conversation", "1").getMessages().stream()
                            .flatMap(message -> message.getActivities().stream())
                            .anyMatch(activity -> activity.getStatus()
                                    == com.agentsquad.squad.v2.enums.ChatExecutionStatus.RUNNING))
                    .next().block(Duration.ofSeconds(10))).isNotNull();
            var supplement = AguiSessionTest.input("tool-supplement", "工具完成后只回复完成");
            supplement.setObservedTurnId(receipt.getTurnId());
            assertThat(service.send(supplement, "1").getDisposition())
                    .isEqualTo(com.agentsquad.squad.v2.enums.ChatDisposition.STEERING);
            assertThat(session.await(session.task(receipt.getInputId())).block(Duration.ofSeconds(20)).status())
                    .isEqualTo("completed");
            var snapshot = sessions.read("conversation", "1");
            assertThat(snapshot.getInputs().getFirst().getStatus())
                    .isEqualTo(com.agentsquad.squad.v2.enums.ChatInputStatus.APPLIED);
            var activities = snapshot.getMessages().stream().flatMap(message -> message.getActivities().stream()).toList();
            assertThat(activities).hasSize(1);
            assertThat(activities.getFirst().getStatus())
                    .isEqualTo(com.agentsquad.squad.v2.enums.ChatExecutionStatus.COMPLETED);

        }
    }
}
