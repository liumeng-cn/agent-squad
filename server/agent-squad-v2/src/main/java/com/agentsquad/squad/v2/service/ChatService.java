package com.agentsquad.squad.v2.service;

import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.event.AguiEventType;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import java.util.List;

import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 持续对话服务
 * <p>
 * 通过官方 AG-UI 适配器交付聊天事件，执行取消遵循 SDK 流生命周期。
 */
@Slf4j
@Service
public class ChatService {
    @Resource
    private ObjectProvider<HarnessAgent> agentProvider;

    /**
     * 使用官方 AG-UI 适配器驱动 Harness，身份始终取自服务端。
     * 只交付公开事件，不回传原始事件或输入；取消订阅遵循 SDK 流的生命周期。
     */
    public Flux<AguiEvent> sendAgui(ChatMessageDTO request, String userId) {
        return Flux.defer(() -> {
            var agent = agentProvider.getIfAvailable();
            if (agent == null) {
                return Flux.just(new AguiEvent.RunError(
                        request.getSessionId(), request.getRequestId(), "聊天模型尚未启用", "MODEL_DISABLED"));
            }
            var input = new RunAgentInput(
                    request.getSessionId(), request.getRequestId(),
                    List.of(AguiMessage.userMessage(
                            request.getRequestId(), request.getMessage().trim())),
                    null, null, null, null, null);
            var config = AguiAdapterConfig.builder()
                    .enableReasoning(true).build();
            var context = RuntimeContext.builder().userId(userId).sessionId(request.getSessionId()).build();
            return new AguiAgentAdapter(agent, config).run(input, context)
                    .filter(event -> event.getType() != AguiEventType.RAW)
                    .map(event -> event instanceof AguiEvent.RunStarted
                            ? new AguiEvent.RunStarted(request.getSessionId(), request.getRequestId())
                            : event);
        }).subscribeOn(Schedulers.boundedElastic());
    }

}
