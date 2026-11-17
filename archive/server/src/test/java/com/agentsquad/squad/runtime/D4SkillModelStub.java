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
                // 技能版本 ID 已统一为雪花 Long，目录标识形如 tone_platform-skill:1234567890。
                var match = Pattern.compile("tone_platform-skill:\\d+").matcher(request);
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
            boolean streaming = json.readTree(request).path("stream").asBoolean();
            byte[] response = streaming ? stream(json, body).getBytes(StandardCharsets.UTF_8) : body;
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
    }

    /**
     * 原生 SDK 使用流式协议调用模型，夹具把整条消息包装成单个增量块并附带用量，避免缺少用量事件。
     */
    private String stream(ObjectMapper json, byte[] body) throws java.io.IOException {
        var chunk = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body);
        var choice = (com.fasterxml.jackson.databind.node.ObjectNode) chunk.path("choices").get(0);
        var message = choice.remove("message");
        int index = 0;
        for (var tool : message.path("tool_calls")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) tool).put("index", index++);
        }
        choice.set("delta", message);
        chunk.put("object", "chat.completion.chunk");
        return "data: " + json.writeValueAsString(chunk) + "\n\ndata: [DONE]\n\n";
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
