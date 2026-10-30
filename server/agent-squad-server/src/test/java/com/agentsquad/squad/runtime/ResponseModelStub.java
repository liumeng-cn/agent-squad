package com.agentsquad.squad.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 普通回复的本地模型协议桩。
 * <p>
 * 实际SDK调用此HTTP端点，不接外部供应商；记录受控测试材料与调用次数以验证恢复没有再次推理。
 */
class ResponseModelStub implements AutoCloseable {
    final AtomicInteger calls = new AtomicInteger();
    final List<String> requests = new CopyOnWriteArrayList<>();
    final CountDownLatch blocked = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    private final ExecutorService threads = Executors.newFixedThreadPool(4);
    private final HttpServer server;

    ResponseModelStub() throws Exception {
        this(false);
    }

    ResponseModelStub(boolean blockFinal) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(threads);
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(request);
            int call = calls.incrementAndGet();
            if (blockFinal && call == 2) {
                blocked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            String response;
            if (call % 2 == 1) {
                String answer = request.contains("他叫什么") ? "他叫小明。" : "记住了，小明。";
                String arguments = new ObjectMapper().valueToTree("{\"outcome\":\"SUCCEEDED\",\"finalAnswer\":\"" + answer + "\"}").toString();
                response = """
                        {"id":"reply-%s","object":"chat.completion","created":1,"model":"test-model",
                         "choices":[{"index":0,"message":{"role":"assistant","content":null,
                         "tool_calls":[{"id":"completion-%s","type":"function","function":{"name":"propose_completion","arguments":%s}}]},"finish_reason":"tool_calls"}],
                         "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                        """.formatted(call, call, arguments);
            } else {
                response = """
                        {"id":"reply-end","object":"chat.completion","created":1,"model":"test-model",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"等待平台发布"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":5,"completion_tokens":5,"total_tokens":10}}
                        """;
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
        threads.shutdownNow();
    }
}
