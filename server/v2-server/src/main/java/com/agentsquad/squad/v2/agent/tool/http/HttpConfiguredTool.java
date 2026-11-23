package com.agentsquad.squad.v2.agent.tool.http;

import com.agentsquad.squad.v2.bo.HttpToolRequestBO;
import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.bo.ToolDefinitionBO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * 配置生成的 HTTP 工具
 * <p>
 * 按工具版本中的请求模板调用接口：参数由模型提供，凭据由服务端配置注入；
 * 只读取前 1 MiB 响应，输出超过 8000 字符截断，日志只记录工具名、方法、主机、状态码和耗时。
 */
@Slf4j
public class HttpConfiguredTool implements AgentTool {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private static final int MAX_OUTPUT_CHARS = 8000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String name;

    private final String description;

    private final Map<String, Object> inputSchema;

    private final boolean readOnly;

    private final HttpToolSpecBO spec;

    private final String credential;

    private final HttpClient client;

    public HttpConfiguredTool(ToolDefinitionBO definition, HttpClient client) {
        this.name = definition.getExposedName();
        this.description = definition.getDescription();
        this.inputSchema = definition.getInputSchema();
        this.readOnly = definition.isReadOnly();
        this.spec = definition.getHttpSpec();
        this.credential = definition.getCredential();
        this.client = client;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return inputSchema;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 调用接口
     * <p>
     * 渲染失败、超时、网络异常和非 2xx 响应都转换为工具错误结果，交给模型决定下一步；请求本身不重试。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        HttpToolRequestBO request;
        try {
            request = HttpToolTemplate.render(spec, param.getInput(), credential);
        } catch (IllegalArgumentException invalid) {
            return Mono.just(ToolResultBlock.error(invalid.getMessage()));
        }

        long startedAt = System.nanoTime();
        HttpRequest httpRequest = toHttpRequest(request);
        return Mono.fromFuture(() -> client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream()))
                .map(response -> toResult(request, response, startedAt))
                .onErrorResume(error -> Mono.just(failure(request, error, startedAt)));
    }

    /**
     * 渲染请求摘要
     * <p>
     * 试运行回显使用，凭据已遮蔽；参数无法渲染时返回错误说明。
     */
    public String describeRequest(Map<String, Object> arguments) {
        try {
            return HttpToolTemplate.render(spec, arguments, credential).getSummary();
        } catch (IllegalArgumentException invalid) {
            return spec.getMethod() + " " + invalid.getMessage();
        }
    }

    private HttpRequest toHttpRequest(HttpToolRequestBO request) {
        HttpRequest.BodyPublisher publisher = request.getBody() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.getBody(), StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.getUri())
                .timeout(Duration.ofMillis(spec.getTimeoutMs()))
                .method(request.getMethod().name(), publisher);
        request.getHeaders().forEach(builder::header);
        return builder.build();
    }

    private ToolResultBlock toResult(HttpToolRequestBO request, HttpResponse<InputStream> response, long startedAt) {
        String body = readLimited(response.body());
        long elapsed = elapsedMillis(startedAt);
        int status = response.statusCode();
        log.info("HTTP工具调用完成，tool={}，method={}，host={}，status={}，耗时={}ms",
                name, request.getMethod(), request.getUri().getHost(), status, elapsed);

        if (status < 200 || status >= 300) {
            return ToolResultBlock.error("接口返回 HTTP " + status + "：" + truncate(mask(body)));
        }

        String pointer = spec.getResultPointer();
        if (pointer == null || pointer.isBlank()) {
            return ToolResultBlock.text(truncate(mask(body)));
        }
        return extract(body, pointer);
    }

    private ToolResultBlock extract(String body, String pointer) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JsonProcessingException invalid) {
            return ToolResultBlock.error("接口响应不是 JSON，无法按 " + pointer + " 提取结果");
        }

        JsonNode node = root.at(pointer);
        if (node.isMissingNode()) {
            return ToolResultBlock.error("接口响应中不存在路径 " + pointer);
        }
        String text = node.isTextual() ? node.asText() : node.toString();
        return ToolResultBlock.text(truncate(mask(text)));
    }

    private ToolResultBlock failure(HttpToolRequestBO request, Throwable error, long startedAt) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        long elapsed = elapsedMillis(startedAt);
        boolean timeout = cause instanceof HttpTimeoutException || cause instanceof TimeoutException;
        log.warn("HTTP工具调用失败，tool={}，method={}，host={}，原因={}，耗时={}ms",
                name, request.getMethod(), request.getUri().getHost(), cause.getClass().getSimpleName(), elapsed);
        if (timeout) {
            return ToolResultBlock.error("接口调用超时（" + spec.getTimeoutMs() + "ms）");
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return ToolResultBlock.error("接口调用失败：" + mask(message));
    }

    private String readLimited(InputStream input) {
        try (input) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new CompletionException(failed);
        }
    }

    private String mask(String text) {
        return HttpToolTemplate.mask(text, credential);
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS) + "\n…（结果过长，已截断，原文 " + text.length() + " 字符）";
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
