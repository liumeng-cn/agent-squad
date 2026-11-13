package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.converter.ChatEventConverter;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.enums.ChatEventType;
import com.agentsquad.squad.v2.vo.ChatEventVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

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

    @Resource
    private ChatEventConverter eventConverter;

    /**
     * 使用官方 AG-UI 适配器驱动 Harness，身份始终取自服务端。
     * 只交付公开事件，不回传原始事件或输入；取消订阅遵循 SDK 流的生命周期。
     */
    public Flux<ChatEventVO> send(ChatMessageDTO request, String userId) {
        return Flux.defer(() -> {

                    var sessionId = request.getSessionId();
                    var requestId = request.getRequestId();
                    var agent = agentProvider.getIfAvailable();
                    if (agent == null) {
                        var failure = new ChatEventVO();
                        failure.setType(ChatEventType.RUN_ERROR);
                        failure.setThreadId(sessionId);
                        failure.setRunId(requestId);
                        failure.setMessage("聊天模型尚未启用");
                        return Flux.just(failure);
                    }

                    var input = new RunAgentInput(
                            sessionId,
                            requestId,
                            List.of(AguiMessage.userMessage(
                                    sessionId,
                                    request.getMessage().trim())
                            ),
                            null,
                            null,
                            null,
                            null,
                            null
                    );

                    var config = AguiAdapterConfig.builder()
                            .enableReasoning(true)
                            .build();

                    var context = RuntimeContext.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .build();

                    return new AguiAgentAdapter(agent, config)
                            .run(input, context)
                            .flatMapIterable(event -> eventConverter.convert(event)
                                    .stream()
                                    .toList());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

}
