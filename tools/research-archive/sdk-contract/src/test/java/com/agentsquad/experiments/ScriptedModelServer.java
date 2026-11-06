package com.agentsquad.experiments;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 固定响应的本地模型协议桩
 * <p>
 * 仅监听回环地址，按调用顺序返回受控响应；不保存请求正文或连接真实供应商。
 */
final class ScriptedModelServer implements AutoCloseable {
    private final HttpServer server;
    private final List<String> responses;
    private final boolean streaming;
    private final AtomicInteger calls = new AtomicInteger();

    ScriptedModelServer(List<String> responses, boolean streaming) throws IOException {
        this.responses = List.copyOf(responses);
        this.streaming = streaming;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int index = calls.getAndIncrement();
            byte[] body = this.responses.get(Math.min(index, this.responses.size() - 1))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
                output.flush();
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    int calls() {
        return calls.get();
    }

    static String text(String finishReason) {
        String reason = finishReason == null ? "null" : "\"" + finishReason + "\"";
        return """
                {"id":"probe-text","object":"chat.completion","created":1,"model":"probe",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"测试回答"},"finish_reason":%s}],
                 "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                """.formatted(reason);
    }

    static String tools(boolean mixed) {
        String extra = mixed ? ",{\"id\":\"lookup-call\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{}\"}}" : "";
        return """
                {"id":"probe-tools","object":"chat.completion","created":1,"model":"probe",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                 "tool_calls":[{"id":"finish-call","type":"function","function":{"name":"finish","arguments":"{}"}}%s]},"finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                """.formatted(extra);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
