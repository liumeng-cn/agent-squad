package com.agentsquad.squad.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用内部身份断言。
 *
 * <p>业务 HTTP 测试通过真实拦截器校验签名断言，不再伪造裸 `X-User-*`；
 * 固定密钥与 `src/test/resources/application.properties` 保持一致，仅用于隔离测试库。</p>
 */
public final class TestAssertion {

    public static final String KID = "k-test";
    public static final String SECRET = "test-only-assertion-secret-0123456789abcdef";

    private static final ObjectMapper JSON = new ObjectMapper();

    private TestAssertion() {
    }

    /** 按当前 MockMvc 请求的方法和路径生成断言，附加到请求头。 */
    public static RequestPostProcessor as(String userId, String... roles) {
        return request -> {
            List<String> roleList = roles == null || roles.length == 0 ? List.of("USER") : List.of(roles);
            request.addHeader("X-Squad-Assertion",
                    sign(userId, roleList, request.getMethod(), request.getRequestURI(), Instant.now()));
            return request;
        };
    }

    public static String sign(String userId, List<String> roles, String method, String path, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "agent-squad-gateway");
        payload.put("aud", "agent-squad-server");
        payload.put("sub", userId);
        payload.put("name", userId);
        payload.put("roles", roles);
        payload.put("authVersion", 0);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(30).getEpochSecond());
        payload.put("method", method);
        payload.put("path", path);
        payload.put("requestId", "test");
        payload.put("kid", KID);
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String encoded = encoder.encodeToString(JSON.writeValueAsBytes(payload));
            return encoded + "." + encoder.encodeToString(hmac(SECRET.getBytes(StandardCharsets.UTF_8), encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("测试断言签发失败", exception);
        }
    }

    private static byte[] hmac(byte[] key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 不可用", exception);
        }
    }
}
