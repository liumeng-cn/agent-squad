package com.agentsquad.squad.nativechat;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Master + 三个独立 SubAgent 的并行执行实验。 */
@Service
public class NativeSubagentService {
    @Value("${agentsquad.native-chat.api-key:}")
    private String apiKey;
    @Value("${agentsquad.native-chat.base-url:https://api.deepseek.com}")
    private String baseUrl;
    @Value("${agentsquad.native-chat.model:deepseek-chat}")
    private String modelName;

    public SseEmitter stream(String task) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "原生聊天未配置 DEEPSEEK_API_KEY");
        }
        if (task == null || task.isBlank() || task.length() > 8000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务不能为空且不能超过 8000 字");
        }

        Toolkit masterToolkit = new Toolkit(ToolkitConfig.builder().parallel(true).build());
        registerSubagent(masterToolkit, "calculator", "call_calculator",
                "负责数字计算。收到任务后必须调用 add_numbers 工具一次，再汇报工具返回值。",
                NativeSubagentTools.Calculator::new);
        registerSubagent(masterToolkit, "word_counter", "call_word_counter",
                "负责文本统计。收到任务后必须调用 count_words 工具一次，再汇报工具返回值。",
                NativeSubagentTools.WordCounter::new);
        registerSubagent(masterToolkit, "text_reverser", "call_text_reverser",
                "负责文本反转。收到任务后必须调用 reverse_text 工具一次，再汇报工具返回值。",
                NativeSubagentTools.TextReverser::new);

        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Disposable> execution = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Runnable cancel = () -> {
            cancelled.set(true);
            Disposable current = execution.get();
            if (current != null) current.dispose();
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());

        Disposable subscription = Flux.using(
                () -> ReActAgent.builder()
                        .name("ParallelSubagentMaster")
                        .sysPrompt("你是多智能体协调助手。每次收到任务，必须拆成三个独立子任务，并在同一轮模型响应中同时调用 "
                                + "call_calculator、call_word_counter、call_text_reverser。"
                                + "三个工具分别负责计算、文本统计、文本反转。必须一次性发起全部三个调用，不得串行等待，不得遗漏。"
                                + "每个子智能体会自行调用它唯一拥有的工具。等待三个子智能体都返回后，分别整理结果并汇总。"
                                + "不要自己直接完成工具任务，不要伪造工具结果。")
                        .model(OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl)
                                .modelName(modelName).stream(true).build())
                        .toolkit(masterToolkit)
                        .build(),
                master -> master.streamEvents(new UserMessage(task)),
                ReActAgent::close)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(event -> send(emitter, event), error -> {
                    try {
                        emitter.send(SseEmitter.event().name("STREAM_ERROR")
                                .data(Map.of("message", "多 SubAgent 执行失败，请检查服务端日志和模型配置"),
                                        MediaType.APPLICATION_JSON));
                    } catch (IOException ignored) {
                        // 客户端可能已经断开。
                    }
                    emitter.complete();
                }, emitter::complete);
        execution.set(subscription);
        if (cancelled.get()) subscription.dispose();
        return emitter;
    }

    private void registerSubagent(Toolkit parentToolkit, String name, String toolName,
                                  String instructions, Supplier<Object> toolSupplier) {
        parentToolkit.registration()
                .subAgent(() -> {
                    Toolkit childToolkit = new Toolkit();
                    childToolkit.registerTool(toolSupplier.get());
                    return ReActAgent.builder()
                            .name(name)
                            .sysPrompt(instructions)
                            .model(OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl)
                                    .modelName(modelName).stream(true).build())
                            .toolkit(childToolkit)
                            .build();
                }, SubAgentConfig.builder()
                        .toolName(toolName)
                        .description("创建并调用 " + name + " 子智能体，它会调用自己的专属工具完成子任务。")
                        .forwardEvents(true)
                        .build())
                .apply();
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().id(event.getId()).name(event.getType().name())
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException error) {
            throw new IllegalStateException("SSE 连接已断开", error);
        }
    }
}
