package com.agentsquad.squad.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 技能发现与实际读取的模型协议桩。
 * <p>
 * 通过正式SDK请求选择目录中的技能，再提交完成候选；不把直接调用服务代替框架按需加载。
 */
final class D4SkillModelStub implements AutoCloseable {
    final List<String> requests = new CopyOnWriteArrayList<>();
    final AtomicInteger calls = new AtomicInteger();
    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();

    D4SkillModelStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(request);
            int call = calls.incrementAndGet();
            Object message;
            String finish;
            if (call == 1) {
                var match = Pattern.compile("tone_platform-skill:[a-f0-9-]{36}").matcher(request);
                if (!match.find()) {
                    throw new IllegalStateException("框架目录没有可选择的固定技能");
                }
                message = tool(call, "load_skill_through_path", Map.of("skillId", match.group(), "path", "SKILL.md"));
                finish = "tool_calls";
            } else if (call == 2) {
                message = tool(call, "propose_completion", Map.of("outcome", "SUCCEEDED", "finalAnswer", "已依据固定技能和个人记忆完成回复"));
                finish = "tool_calls";
            } else {
                message = Map.of("role", "assistant", "content", "完成");
                finish = "stop";
            }
            byte[] body = json.writeValueAsBytes(Map.of("id", "d4-" + call, "object", "chat.completion", "created", 1,
                    "model", "test-model", "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", finish)),
                    "usage", Map.of("prompt_tokens", 5, "completion_tokens", 5, "total_tokens", 10)));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    private Map<String, Object> tool(int call, String name, Object arguments) throws java.io.IOException {
        return Map.of("role", "assistant", "tool_calls", List.of(Map.of("id", "d4-call-" + call,
                "type", "function", "function", Map.of("name", name, "arguments", json.writeValueAsString(arguments)))));
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
