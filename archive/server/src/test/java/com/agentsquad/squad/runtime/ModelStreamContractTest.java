package com.agentsquad.squad.runtime;

import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定 SDK 的模型增量协议探针。
 * <p>
 * 仅调用本地 HTTP 桩，验证可解析文本块；模型文本不因此获得用户可见交付权。
 */
class ModelStreamContractTest {
    @Test
    void shouldReadTextChunksFromLocalOpenAiStream() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String stream = "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"stub\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"正文\"},\"finish_reason\":null}]}\n\n"
                    + "data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"stub\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: [DONE]\n\n";
            byte[] body = stream.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            var model = OpenAIChatModel.builder().apiKey("local-stub-only")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .modelName("stub").stream(true).build();
            var chunks = model.stream(List.of(Msg.builder().role(MsgRole.USER).textContent("测试").build()),
                    List.of(), null).collectList().block(Duration.ofSeconds(10));
            assertThat(chunks).isNotEmpty();
            assertThat(chunks.stream().flatMap(chunk -> chunk.getContent().stream())
                    .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                    .map(TextBlock::getText).toList()).contains("正文");
        } finally {
            server.stop(0);
        }
    }
}
