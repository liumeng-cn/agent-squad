package com.agentsquad.squad.runtime;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自然回复的受控本地模型
 * <p>
 * 只返回公开答案，允许测试在模型完成前断开SSE；所有请求均留在隔离测试内存。
 */
final class NaturalReplyModelStub implements AutoCloseable {
    final AtomicInteger calls = new AtomicInteger();
    final List<String> requests = new CopyOnWriteArrayList<>();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch firstChunk = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    private final ExecutorService threads = Executors.newFixedThreadPool(2);
    private final HttpServer server;

    NaturalReplyModelStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(threads);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            entered.countDown();
            try (var output = exchange.getResponseBody()) {
                if (!firstChunk.await(20, TimeUnit.SECONDS)) {
                    return;
                }
                write(output, "{\"id\":\"natural-reply\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"TEST_PRIVATE_REASONING\"},\"finish_reason\":null}]}");
                write(output, "{\"id\":\"natural-reply\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"页面断开后，\"},\"finish_reason\":null}]}");
                if (!release.await("true".equals(System.getenv("SQUAD_BROWSER_PROBE")) ? 600 : 20, TimeUnit.SECONDS)) {
                    return;
                }
                write(output, "{\"id\":\"natural-reply\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"回答仍然完成。\"},\"finish_reason\":null}]}");
                write(output, "{\"id\":\"natural-reply\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}");
                write(output, "{\"id\":\"natural-reply\",\"choices\":[],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":5,\"total_tokens\":10}}");
                write(output, "[DONE]");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }

        });
        server.start();
    }

    private void write(java.io.OutputStream output, String value) throws java.io.IOException {
        output.write(("data: " + value + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Override
    public void close() {
        firstChunk.countDown();
        release.countDown();
        server.stop(0);
        threads.shutdownNow();
    }
}
