package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.service.ChatService;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AG-UI 原生执行验证
 * <p>
 * 使用受控模型验证官方适配器真实运行、多轮会话与正文交付。
 */
class AguiSessionTest {
    @TempDir
    Path workspace;

    @Test
    void streamsNativeEventsAndKeepsConversation() {
        var model = new ScriptedChatModel();
        try (var agent = HarnessAgent.builder().name("test").agentId("test")
                .sysPrompt("测试").model(model).workspace(workspace).build()) {
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("agent", agent);
            var service = new ChatService();
            ReflectionTestUtils.setField(service, "agentProvider", beans.getBeanProvider(HarnessAgent.class));
            ReflectionTestUtils.setField(service, "eventConverter", new com.agentsquad.squad.v2.converter.ChatEventConverter());
            for (int i = 0; i < 2; i++) {
                var request = new ChatMessageDTO();
                request.setSessionId("agui-conversation");
                request.setRequestId("request-" + i);
                request.setMessage("问题" + i);
                var events = service.send(request, "1").collectList().block(Duration.ofSeconds(30));
                assertThat(events).isNotEmpty();
                assertThat(events.getLast().getType().name()).isEqualTo("RUN_FINISHED");
                assertThat(events).anyMatch(event -> event.getType().name().equals("TEXT_MESSAGE_CONTENT"));

            }
            assertThat(model.inputs).anyMatch(messages -> messages.stream()
                    .filter(message -> message.getRole() == io.agentscope.core.message.MsgRole.USER).count() == 2);
        }
    }
}
