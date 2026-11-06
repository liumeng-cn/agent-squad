package com.agentsquad.experiments;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.agentscope.core.tool.Toolkit;
import java.nio.file.Path;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型增量内容与用量边界实验
 * <p>
 * 验证 SDK 可区分内容类型及尾部用量，并记录正常取消和缺失结束原因；不实现页面发布规则。
 */
class SdkStreamContractTest {
    @Test
    void harnessPreservesFinishReasonWithUsageOnlyTail(@TempDir Path directory) throws Exception {
        String stream = chunk("\"content\":\"完整回答\"", null)
                + chunk("", "stop")
                + "data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"probe\",\"choices\":[],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":4,\"total_tokens\":11}}\n\n"
                + "data: [DONE]\n\n";
        try (var server = new ScriptedModelServer(List.of(stream), true);
             var agent = SdkExecutionContractTest.agent(server, directory, new Toolkit(), 2, true)) {
            var session = SdkExecutionContractTest.session(agent);
            var receipt = session.submit("stream-tail", "本地流式实验");
            assertThat(session.await(receipt).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            var end = session.log().readAfter(0, 1000).stream()
                    .filter(event -> event.type().equals("model/end")).findFirst().orElseThrow();
            assertThat(session.log().readAfter(0, 1000)).anyMatch(event ->
                    event.type().equals("model/chunk") && event.data().get("chunk") instanceof Map<?, ?> chunk
                            && "stop".equals(chunk.get("finishReason")));
            assertThat(end.data().get("finishReason")).isEqualTo("stop");
            assertThat(end.payloadJson()).contains("7", "4");
        }
    }

    @Test
    void separatesTextThinkingToolArgumentsAndUsageOnlyTail() throws Exception {
        String stream = chunk("\"reasoning_content\":\"内部实验思考\"", null)
                + chunk("\"content\":\"公开🙂正文\"", null)
                + chunk("\"tool_calls\":[{\"index\":0,\"id\":\"call\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"key\\\":\\\"内部参数\\\"}\"}}]", "tool_calls")
                + "data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"probe\",\"choices\":[],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":4,\"total_tokens\":11}}\n\n"
                + "data: [DONE]\n\n";
        try (var server = new ScriptedModelServer(List.of(stream), true)) {
            var responses = model(server).stream(input(), List.of(), null).collectList().block(Duration.ofSeconds(10));
            assertThat(responses).isNotEmpty();
            var blocks = responses.stream().flatMap(response -> response.getContent().stream()).toList();
            assertThat(blocks).anyMatch(ThinkingBlock.class::isInstance);
            assertThat(blocks).anyMatch(ToolUseBlock.class::isInstance);
            String visible = blocks.stream().filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                    .map(TextBlock::getText).reduce("", String::concat);
            assertThat(visible).contains("公开🙂正文").doesNotContain("内部实验思考", "内部参数");
            assertThat(responses).anyMatch(response -> response.getUsage() != null
                    && response.getUsage().getInputTokens() == 7 && response.getUsage().getOutputTokens() == 4);
        }
    }

    @Test
    void eofWithoutFinishCanCompletePublisherWithoutProvingAnswerComplete() throws Exception {
        try (var server = new ScriptedModelServer(List.of(chunk("\"content\":\"未完成文本\"", null)), true)) {
            var responses = model(server).stream(input(), List.of(), null).collectList().block(Duration.ofSeconds(10));
            assertThat(responses).isNotEmpty();
            assertThat(responses).allMatch(response -> response.getFinishReason() == null);
        }
    }

    @Test
    void downstreamCancellationIsObservable() throws Exception {
        String stream = chunk("\"content\":\"第一块\"", null)
                + chunk("\"content\":\"第二块\"", "stop") + "data: [DONE]\n\n";
        try (var server = new ScriptedModelServer(List.of(stream), true)) {
            AtomicReference<SignalType> terminal = new AtomicReference<>();
            var response = model(server).stream(input(), List.of(), null)
                    .doFinally(terminal::set).take(1).collectList().block(Duration.ofSeconds(10));
            assertThat(response).hasSize(1);
            assertThat(terminal.get()).isEqualTo(SignalType.CANCEL);
        }
    }

    private OpenAIChatModel model(ScriptedModelServer server) {
        return OpenAIChatModel.builder().apiKey("local-probe-only").baseUrl(server.baseUrl())
                .modelName("probe").stream(true).build();
    }

    private List<Msg> input() {
        return List.of(Msg.builder().role(MsgRole.USER).textContent("本地实验").build());
    }

    private String chunk(String delta, String reason) {
        return "data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"probe\",\"choices\":[{\"index\":0,\"delta\":{"
                + delta + "},\"finish_reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}]}\n\n";
    }
}
