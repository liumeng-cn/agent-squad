package com.agentsquad.squad.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 业务服务的网关身份断言校验器。
 *
 * <p>只从 `X-Squad-Assertion` 建立主体，严格校验签名、密钥版本、受众、方法、原始路径与时间窗；
 * 不读取浏览器可伪造的 `X-User-*`。任何缺失、重复、超长、畸形、篡改或过期断言都返回 401。</p>
 */
@Slf4j
@Component
public class AssertionVerifier {

    private static final String HEADER = "X-Squad-Assertion";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_ASSERTION_LENGTH = 4096;
    private static final int MAX_PAYLOAD_LENGTH = 2048;
    private static final Set<String> ROLES = Set.of("USER", "ADMIN", "OPERATOR");

    @Resource
    private AssertionProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    private byte[] currentKey;
    private byte[] previousKey;

    @PostConstruct
    void initialize() {
        currentKey = requireSecret(properties.getCurrentSecret(), "agentsquad.assertion.current-secret");
        previousKey = optionalSecret(properties.getPreviousSecret(), "agentsquad.assertion.previous-secret");
        if ((properties.getPreviousKid() == null || properties.getPreviousKid().isBlank()) != (previousKey == null)) {
            throw new IllegalStateException("上一把断言密钥的 kid 与 secret 必须同时配置或同时留空");
        }
        if (properties.getTtlSeconds() <= 0 || properties.getTtlSeconds() > 300) {
            throw new IllegalStateException("断言有效期必须在 1–300 秒之间");
        }
    }

    /**
     * 校验当前请求的内部断言并返回可信主体；失败不返回任何断言或密钥细节。
     */
    public AuthView verify(HttpServletRequest request) {
        try {
            return doVerify(request);
        } catch (InvalidAssertionException exception) {
            log.warn("内部身份断言校验失败 reason={} method={} path={} requestId={}",
                    exception.getMessage(), request.getMethod(), request.getRequestURI(),
                    request.getHeader("X-Request-Id"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少或无效的内部身份断言");
        }
    }

    private AuthView doVerify(HttpServletRequest request) {
        List<String> headers = Collections.list(request.getHeaders(HEADER));
        if (headers.size() != 1) {
            throw new InvalidAssertionException(headers.isEmpty() ? "missing" : "duplicate-header");
        }
        String assertion = headers.get(0);
        if (assertion == null || assertion.isBlank() || assertion.length() > MAX_ASSERTION_LENGTH) {
            throw new InvalidAssertionException("length");
        }
        int separator = assertion.lastIndexOf('.');
        if (separator <= 0 || separator == assertion.length() - 1) {
            throw new InvalidAssertionException("format");
        }
        String encodedPayload = assertion.substring(0, separator);
        String encodedSignature = assertion.substring(separator + 1);
        if (encodedPayload.length() > MAX_PAYLOAD_LENGTH) {
            throw new InvalidAssertionException("payload-length");
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        byte[] signature;
        byte[] payloadBytes;
        try {
            signature = decoder.decode(encodedSignature);
            payloadBytes = decoder.decode(encodedPayload);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAssertionException("base64");
        }
        JsonNode payload = parse(payloadBytes);
        byte[] key = keyFor(text(payload, "kid", 32));
        if (key == null) {
            throw new InvalidAssertionException("kid");
        }
        if (!MessageDigest.isEqual(hmac(key, encodedPayload), signature)) {
            throw new InvalidAssertionException("signature");
        }

        if (!properties.getIssuer().equals(text(payload, "iss", 64))) {
            throw new InvalidAssertionException("issuer");
        }
        if (!properties.getAudience().equals(text(payload, "aud", 64))) {
            throw new InvalidAssertionException("audience");
        }
        String subject = text(payload, "sub", 64);
        try {
            UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAssertionException("subject");
        }
        String method = text(payload, "method", 16);
        if (!method.equals(request.getMethod())) {
            throw new InvalidAssertionException("method");
        }
        String path = text(payload, "path", 2048);
        if (!path.equals(request.getRequestURI()) || path.indexOf('?') >= 0) {
            throw new InvalidAssertionException("path");
        }
        verifyTime(payload);
        return new AuthView(subject, text(payload, "name", 64), roles(payload), null);
    }

    private void verifyTime(JsonNode payload) {
        long issuedAt = number(payload, "iat");
        long expiresAt = number(payload, "exp");
        long now = Instant.now().getEpochSecond();
        long skew = Math.max(0, properties.getClockSkewSeconds());
        if (expiresAt <= issuedAt || expiresAt - issuedAt > properties.getTtlSeconds()) {
            throw new InvalidAssertionException("ttl");
        }
        if (issuedAt > now + skew || expiresAt < now - skew) {
            throw new InvalidAssertionException("expired");
        }
    }

    private JsonNode parse(byte[] payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || !node.isObject()) {
                throw new InvalidAssertionException("payload");
            }
            return node;
        } catch (InvalidAssertionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidAssertionException("json");
        }
    }

    private List<String> roles(JsonNode payload) {
        JsonNode node = payload.get("roles");
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > 3) {
            throw new InvalidAssertionException("roles");
        }
        Set<String> roles = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String role = item.isTextual() ? item.asText() : "";
            if (!ROLES.contains(role)) {
                throw new InvalidAssertionException("role-value");
            }
            roles.add(role);
        }
        if (roles.isEmpty()) {
            throw new InvalidAssertionException("roles-empty");
        }
        return new ArrayList<>(roles);
    }

    private static String text(JsonNode payload, String field, int maxLength) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isTextual()) {
            throw new InvalidAssertionException(field);
        }
        String value = node.asText();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new InvalidAssertionException(field + "-length");
        }
        return value;
    }

    private static long number(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isIntegralNumber()) {
            throw new InvalidAssertionException(field);
        }
        return node.asLong();
    }

    private byte[] keyFor(String kid) {
        if (kid.equals(properties.getCurrentKid())) {
            return currentKey;
        }
        if (previousKey != null && !properties.getPreviousKid().isBlank()
                && kid.equals(properties.getPreviousKid())) {
            return previousKey;
        }
        return null;
    }

    private static byte[] hmac(byte[] key, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 不可用", exception);
        }
    }

    private static byte[] requireSecret(String secret, String name) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少内部身份断言密钥 " + name + "，请通过环境变量注入至少 32 字节随机值");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(name + " 至少需要 32 字节（256 bit）");
        }
        return bytes;
    }

    private static byte[] optionalSecret(String secret, String name) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return requireSecret(secret, name);
    }
}
