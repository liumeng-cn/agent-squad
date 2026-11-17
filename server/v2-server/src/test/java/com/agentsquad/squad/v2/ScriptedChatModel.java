package com.agentsquad.squad.v2;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 可控聊天模型
 * <p>
 * 在真实 Harness 中生成确定的分块正文，不访问模型服务或使用凭据。
 */
public class ScriptedChatModel implements Model {
    final List<List<Msg>> inputs = new CopyOnWriteArrayList<>();

    @Override
    public String getModelName() {
        return "scripted-chat";
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        inputs.add(List.copyOf(messages));
        return Flux.just(
                ChatResponse.builder().id("reply-" + inputs.size())
                        .content(List.of(ThinkingBlock.builder().thinking("测试模型返回的思考文本").build())).build(),
                ChatResponse.builder().id("reply-" + inputs.size())
                        .content(List.of(TextBlock.builder().text("你好，").build())).build(),
                ChatResponse.builder().id("reply-" + inputs.size())
                        .content(List.of(TextBlock.builder().text("世界").build())).finishReason("stop").build());
    }
}
