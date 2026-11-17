package com.agentsquad.squad.nativechat;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 无业务持久化的原生事件聊天。一次请求创建一个 Agent；多轮上下文由页面显式传入。
 */
@Slf4j
@Service
public class NativeChatService {
    @Value("${agentsquad.native-chat.api-key:sk-53611088171449239d5f9a7c2ca07007}")
    private String apiKey;
    @Value("${agentsquad.native-chat.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;
    @Value("${agentsquad.native-chat.model:deepseek-flash}")
    private String modelName;

    public SseEmitter stream(NativeChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "原生聊天未配置 DEEPSEEK_API_KEY");
        }
        List<Msg> input = new ArrayList<>();
        if (request.history() != null) {
            for (NativeChatRequest.HistoryItem item : request.history()) {
                if (item == null || item.content() == null || item.content().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "历史消息不能为空");
                }
                if ("USER".equals(item.role())) input.add(new UserMessage("user", item.content()));
                else if ("ASSISTANT".equals(item.role())) input.add(new AssistantMessage("assistant", item.content()));
                else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只接受 USER/ASSISTANT 历史");
            }
        }
        input.add(new UserMessage("user", request.message()));

        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Disposable> execution = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Runnable cancel = () -> {
            cancelled.set(true);
            Disposable disposable = execution.get();
            if (disposable != null) disposable.dispose();
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());

        Disposable disposable = Flux.using(
                        () -> ReActAgent.builder()
                                .name("NativeDeepSeekChat")
                                .sysPrompt("你是一名准确、清晰的中文聊天助手。根据用户问题直接回答。")
                                .model(OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl)
                                        .modelName(modelName).stream(true).build())
                                .build(),
                        agent -> agent.streamEvents(input),
                        ReActAgent::close)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(event -> send(emitter, event), error -> {
                    // 异常消息只保留类型和摘要，避免把完整请求或上下文写入日志。
                    log.warn("原生聊天流失败 errorType={} message={}",
                            error.getClass().getSimpleName(), error.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("STREAM_ERROR")
                                .data(Map.of("message", "模型调用失败，请检查服务端日志和模型配置"),
                                        MediaType.APPLICATION_JSON));
                    } catch (IOException ignored) {
                        // 客户端可能已经断开。
                    }
                    emitter.complete();
                }, emitter::complete);
        execution.set(disposable);
        if (cancelled.get()) disposable.dispose();
        return emitter;
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            // 事件名和 JSON 数据均保留 AgentScope 原生类型与字段，不另造事件枚举。
            emitter.send(SseEmitter.event().id(event.getId()).name(event.getType().name())
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException error) {
            throw new IllegalStateException("SSE 连接已断开", error);
        }
    }
}
