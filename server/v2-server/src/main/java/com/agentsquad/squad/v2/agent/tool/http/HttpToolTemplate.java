package com.agentsquad.squad.v2.agent.tool.http;

import com.agentsquad.squad.v2.bo.HttpToolRequestBO;
import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.bo.KeyValueBO;
import com.agentsquad.squad.v2.enums.HttpToolMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 工具请求模板
 * <p>
 * 统一校验与渲染 HTTP 工具配置：保存草稿时检查模板结构，试运行和运行时按参数生成实际请求。
 * 占位符写作 {{参数名}}，{{credential}} 引用所选凭据；主机部分不允许占位符，避免模型参数改变请求目标。
 */
public final class HttpToolTemplate {
    public static final String CREDENTIAL = "credential";

    private static final String MASK = "******";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}");

    private static final Set<String> FORBIDDEN_HEADERS = Set.of("host", "content-length", "connection",
            "transfer-encoding", "upgrade", "expect");

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpToolTemplate() {
    }

    /**
     * 校验请求模板
     * <p>
     * 只检查配置本身：地址结构、请求头、请求体模板、凭据占位符与参数定义的一致性。
     *
     * @param parameterNames 参数 Schema 中定义的参数名
     * @return 面向管理员的错误说明，空列表表示通过
     */
    public static List<String> validate(HttpToolSpecBO spec, Set<String> parameterNames) {
        List<String> errors = new ArrayList<>();
        validateUrl(spec.getUrl(), errors);

        for (KeyValueBO header : spec.getHeaders()) {
            if (header.getName() == null || !header.getName().matches("[A-Za-z0-9-]{1,100}")) {
                errors.add("请求头名称只能包含字母、数字和连字符");
            } else if (FORBIDDEN_HEADERS.contains(header.getName().toLowerCase(Locale.ROOT))) {
                errors.add("请求头 " + header.getName() + " 由系统维护，不能配置");
            }
        }

        String body = spec.getBodyTemplate();
        if (body != null && !body.isBlank()) {
            if (!spec.getMethod().isBodyAllowed()) {
                errors.add(spec.getMethod() + " 请求不能配置请求体");
            } else if (!isJsonAfterReplacing(body)) {
                errors.add("请求体模板替换占位符后必须是合法 JSON，参数位置直接写 {{参数名}}，不要加引号");
            }
        }

        Set<String> used = placeholders(spec);
        if (used.contains(CREDENTIAL) && (spec.getCredentialRef() == null || spec.getCredentialRef().isBlank())) {
            errors.add("使用 {{credential}} 时需要选择凭据");
        }
        for (String name : used) {
            if (!CREDENTIAL.equals(name) && !parameterNames.contains(name)) {
                errors.add("占位符 {{" + name + "}} 没有对应的参数定义");
            }
        }

        String pointer = spec.getResultPointer();
        if (pointer != null && !pointer.isBlank() && !pointer.startsWith("/")) {
            errors.add("结果提取路径须为以 / 开头的 JSON Pointer，例如 /data/items");
        }
        return errors;
    }

    /**
     * 渲染实际请求
     * <p>
     * 地址占位符按 URL 编码替换；未被地址使用的参数，GET/DELETE 追加为查询参数，
     * POST/PUT/PATCH 无模板时组成 JSON 请求体，有模板时按模板生成；摘要中的凭据替换为遮蔽值。
     */
    public static HttpToolRequestBO render(HttpToolSpecBO spec, Map<String, Object> arguments, String credential) {
        Set<String> urlNames = new LinkedHashSet<>(namesIn(spec.getUrl()));
        String url = replace(spec.getUrl(), name -> encode(textOf(name, arguments, credential)));
        String maskedUrl = replace(spec.getUrl(), name -> CREDENTIAL.equals(name) ? MASK : encode(textOf(name, arguments, null)));

        // 地址未引用的参数按请求方法放入查询串或请求体。
        Map<String, Object> remaining = new LinkedHashMap<>(arguments);
        remaining.keySet().removeAll(urlNames);
        String body = null;
        if (spec.getMethod().isBodyAllowed()) {
            body = renderBody(spec.getBodyTemplate(), arguments, remaining);
        } else if (!remaining.isEmpty()) {
            String query = queryOf(remaining);
            url = appendQuery(url, query);
            maskedUrl = appendQuery(maskedUrl, query);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (body != null) {
            headers.put("Content-Type", "application/json");
        }
        for (KeyValueBO header : spec.getHeaders()) {
            headers.put(header.getName(), replace(header.getValue(), name -> textOf(name, arguments, credential)));
        }

        HttpToolRequestBO request = new HttpToolRequestBO();
        request.setMethod(spec.getMethod());
        request.setUri(toUri(url));
        request.setHeaders(headers);
        request.setBody(body);
        request.setSummary(spec.getMethod() + " " + maskedUrl);
        return request;
    }

    /**
     * 收集模板中的占位符
     */
    public static Set<String> placeholders(HttpToolSpecBO spec) {
        Set<String> names = new LinkedHashSet<>(namesIn(spec.getUrl()));
        for (KeyValueBO header : spec.getHeaders()) {
            names.addAll(namesIn(header.getValue()));
        }
        names.addAll(namesIn(spec.getBodyTemplate()));
        return names;
    }

    /**
     * 从文本中遮蔽凭据值
     */
    public static String mask(String text, String credential) {
        if (text == null || credential == null || credential.isEmpty()) {
            return text;
        }
        return text.replace(credential, MASK);
    }

    private static void validateUrl(String url, List<String> errors) {
        URI probe;
        try {
            // 用占位值替换后检查结构，确认主机部分没有占位符。
            probe = new URI(replace(url, name -> "x"));
        } catch (URISyntaxException invalid) {
            errors.add("请求地址格式无效");
            return;
        }

        String scheme = probe.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            errors.add("请求地址必须以 http:// 或 https:// 开头");
            return;
        }
        if (probe.getHost() == null || probe.getRawUserInfo() != null || probe.getRawFragment() != null) {
            errors.add("请求地址须包含主机，且不能包含用户信息或 # 片段");
            return;
        }

        int schemeEnd = url.indexOf("://") + 3;
        int pathStart = url.indexOf('/', schemeEnd);
        int queryStart = url.indexOf('?', schemeEnd);
        int authorityEnd = pathStart < 0 ? (queryStart < 0 ? url.length() : queryStart)
                : (queryStart < 0 ? pathStart : Math.min(pathStart, queryStart));
        if (url.substring(schemeEnd, authorityEnd).contains("{{")) {
            errors.add("请求地址的主机和端口不能使用占位符");
        }
    }

    private static String renderBody(String template, Map<String, Object> arguments, Map<String, Object> remaining) {
        if (template == null || template.isBlank()) {
            return toJson(remaining);
        }
        String body = replace(template, name -> toJson(arguments.get(name)));
        if (!isJson(body)) {
            throw new IllegalArgumentException("请求体渲染后不是合法 JSON");
        }
        return body;
    }

    private static String textOf(String name, Map<String, Object> arguments, String credential) {
        if (CREDENTIAL.equals(name)) {
            return credential == null ? "" : credential;
        }
        Object value = arguments.get(name);
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        return toJson(value);
    }

    private static String queryOf(Map<String, Object> values) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof List<?> items) {
                for (Object item : items) {
                    pairs.add(encode(entry.getKey()) + "=" + encode(item instanceof String text ? text : toJson(item)));
                }
            } else if (entry.getValue() != null) {
                String value = entry.getValue() instanceof String text ? text : toJson(entry.getValue());
                pairs.add(encode(entry.getKey()) + "=" + encode(value));
            }
        }
        return String.join("&", pairs);
    }

    private static String appendQuery(String url, String query) {
        if (query.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private static URI toUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("请求地址渲染后格式无效");
        }
    }

    private static Set<String> namesIn(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String replace(String text, Function<String, String> resolver) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolver.apply(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isJsonAfterReplacing(String template) {
        return isJson(replace(template, name -> "null"));
    }

    private static boolean isJson(String text) {
        try {
            JsonNode node = JSON.readTree(text);
            return node != null && !node.isMissingNode();
        } catch (JsonProcessingException invalid) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("参数无法转换为 JSON");
        }
    }
}
