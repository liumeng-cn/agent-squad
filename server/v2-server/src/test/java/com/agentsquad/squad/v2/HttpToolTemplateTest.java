package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.agent.tool.http.HttpToolTemplate;
import com.agentsquad.squad.v2.bo.HttpToolRequestBO;
import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.bo.KeyValueBO;
import com.agentsquad.squad.v2.enums.HttpToolMethod;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HTTP 工具模板测试
 * <p>
 * 验证模板校验规则和按参数渲染请求的结果，确保凭据只出现在实际请求中、不出现在摘要里。
 */
class HttpToolTemplateTest {
    private static HttpToolSpecBO spec(HttpToolMethod method, String url) {
        HttpToolSpecBO spec = new HttpToolSpecBO();
        spec.setMethod(method);
        spec.setUrl(url);
        spec.setHeaders(List.of());
        spec.setTimeoutMs(15000);
        return spec;
    }

    private static KeyValueBO header(String name, String value) {
        KeyValueBO header = new KeyValueBO();
        header.setName(name);
        header.setValue(value);
        return header;
    }

    @Test
    void rejectsPlaceholderInHostAndUndefinedParameters() {
        HttpToolSpecBO hostPlaceholder = spec(HttpToolMethod.GET, "https://{{tenant}}.example.com/orders");
        assertThat(HttpToolTemplate.validate(hostPlaceholder, Set.of("tenant")))
                .anyMatch(error -> error.contains("主机"));

        HttpToolSpecBO undefined = spec(HttpToolMethod.GET, "https://api.example.com/orders/{{orderId}}");
        assertThat(HttpToolTemplate.validate(undefined, Set.of()))
                .anyMatch(error -> error.contains("{{orderId}}"));
    }

    @Test
    void requiresCredentialSelectionAndValidBody() {
        HttpToolSpecBO credential = spec(HttpToolMethod.GET, "https://api.example.com/orders");
        credential.setHeaders(List.of(header("Authorization", "Bearer {{credential}}")));
        assertThat(HttpToolTemplate.validate(credential, Set.of())).anyMatch(error -> error.contains("凭据"));

        HttpToolSpecBO body = spec(HttpToolMethod.POST, "https://api.example.com/orders");
        body.setBodyTemplate("{\"keyword\": \"{{keyword}}\"");
        assertThat(HttpToolTemplate.validate(body, Set.of("keyword"))).anyMatch(error -> error.contains("JSON"));

        HttpToolSpecBO getBody = spec(HttpToolMethod.GET, "https://api.example.com/orders");
        getBody.setBodyTemplate("{\"keyword\": {{keyword}}}");
        assertThat(HttpToolTemplate.validate(getBody, Set.of("keyword"))).anyMatch(error -> error.contains("请求体"));

        HttpToolSpecBO forbidden = spec(HttpToolMethod.GET, "https://api.example.com/orders");
        forbidden.setHeaders(List.of(header("Host", "evil.example.com")));
        assertThat(HttpToolTemplate.validate(forbidden, Set.of())).anyMatch(error -> error.contains("Host"));
    }

    @Test
    void rendersGetWithEncodedPathAndQueryAndMasksCredential() {
        HttpToolSpecBO spec = spec(HttpToolMethod.GET, "https://api.example.com/orders/{{orderId}}?token={{credential}}");
        spec.setHeaders(List.of(header("Authorization", "Bearer {{credential}}")));
        spec.setCredentialRef("order-api");
        assertThat(HttpToolTemplate.validate(spec, Set.of("orderId", "status"))).isEmpty();

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("orderId", "A 01/02");
        arguments.put("status", "已支付");
        HttpToolRequestBO request = HttpToolTemplate.render(spec, arguments, "secret-value");

        assertThat(request.getUri().toString())
                .startsWith("https://api.example.com/orders/A%2001%2F02?token=secret-value")
                .contains("status=%E5%B7%B2%E6%94%AF%E4%BB%98");
        assertThat(request.getHeaders()).containsEntry("Authorization", "Bearer secret-value");
        assertThat(request.getBody()).isNull();
        assertThat(request.getSummary()).doesNotContain("secret-value").contains("token=******");
    }

    @Test
    void rendersPostBodyFromTemplateOrRemainingArguments() {
        HttpToolSpecBO templated = spec(HttpToolMethod.POST, "https://api.example.com/search");
        templated.setBodyTemplate("{\"query\": {\"keyword\": {{keyword}}, \"size\": {{size}}}}");
        HttpToolRequestBO request = HttpToolTemplate.render(templated,
                Map.of("keyword", "发票", "size", 5), null);
        assertThat(request.getBody()).isEqualTo("{\"query\": {\"keyword\": \"发票\", \"size\": 5}}");
        assertThat(request.getHeaders()).containsEntry("Content-Type", "application/json");

        HttpToolSpecBO plain = spec(HttpToolMethod.POST, "https://api.example.com/orders/{{orderId}}/notes");
        HttpToolRequestBO plainRequest = HttpToolTemplate.render(plain,
                Map.of("orderId", "42", "note", "加急"), null);
        assertThat(plainRequest.getUri().getPath()).isEqualTo("/orders/42/notes");
        assertThat(plainRequest.getBody()).isEqualTo("{\"note\":\"加急\"}");
    }

    @Test
    void rejectsBodyThatBecomesInvalidAfterRendering() {
        HttpToolSpecBO spec = spec(HttpToolMethod.POST, "https://api.example.com/search");
        spec.setBodyTemplate("{\"keyword\": \"{{keyword}}\"}");
        assertThatThrownBy(() -> HttpToolTemplate.render(spec, Map.of("keyword", "x"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
