package com.agentsquad.squad.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 网关身份断言的严格校验与密钥轮换。
 *
 * <p>直接驱动业务校验器，覆盖当前/上一把密钥、未知 kid、篡改签名、重复头、
 * 方法/路径不匹配、过期/未来时间与超长有效期；所有失败都必须映射为 401。</p>
 */
class AssertionVerifierTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String CURRENT_KID = "k-test";
    private static final String CURRENT_SECRET = "test-only-assertion-secret-0123456789abcdef";
    private static final String PREVIOUS_KID = "k-prev";
    private static final String PREVIOUS_SECRET = "test-only-previous-secret-0123456789abcdef";
    private static final String PATH = "/api/v1/conversations";

    private AssertionProperties properties;
    private AssertionVerifier verifier;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new AssertionProperties();
        properties.setCurrentKid(CURRENT_KID);
        properties.setCurrentSecret(CURRENT_SECRET);
        properties.setPreviousKid(PREVIOUS_KID);
        properties.setPreviousSecret(PREVIOUS_SECRET);
        verifier = new AssertionVerifier();
        ReflectionTestUtils.setField(verifier, "properties", properties);
        ReflectionTestUtils.setField(verifier, "objectMapper", json);
        verifier.initialize();
    }

    @Test
    void acceptsCurrentAndPreviousKeys() {
        assertThat(verifier.verify(request("GET", PATH,
                sign(CURRENT_KID, CURRENT_SECRET, Instant.now(), 30))).userId()).isEqualTo(USER_ID);
        assertThat(verifier.verify(request("GET", PATH,
                sign(PREVIOUS_KID, PREVIOUS_SECRET, Instant.now(), 30))).userId()).isEqualTo(USER_ID);
    }

    @Test
    void rejectsUnknownKidAndTamperedSignature() {
        assertUnauthorized(request("GET", PATH, sign("unknown", CURRENT_SECRET, Instant.now(), 30)));
        String valid = sign(CURRENT_KID, CURRENT_SECRET, Instant.now(), 30);
        int dot = valid.indexOf('.');
        char firstSignatureChar = valid.charAt(dot + 1);
        String tampered = valid.substring(0, dot + 1) + (firstSignatureChar == 'A' ? "B" : "A")
                + valid.substring(dot + 2);
        assertUnauthorized(request("GET", PATH, tampered));
    }

    @Test
    void rejectsDuplicateMissingAndRawHeaderIdentity() {
        MockHttpServletRequest duplicate = request("GET", PATH,
                sign(CURRENT_KID, CURRENT_SECRET, Instant.now(), 30));
        duplicate.addHeader("X-Squad-Assertion", duplicate.getHeader("X-Squad-Assertion"));
        assertUnauthorized(duplicate);

        MockHttpServletRequest raw = new MockHttpServletRequest("GET", PATH);
        raw.addHeader("X-User-Id", USER_ID);
        raw.addHeader("X-User-Role", "ADMIN");
        assertUnauthorized(raw);
    }

    @Test
    void rejectsWrongMethodAndPath() {
        assertUnauthorized(request("POST", PATH, sign(CURRENT_KID, CURRENT_SECRET, Instant.now(), 30)));
        assertUnauthorized(request("GET", PATH + "/1", sign(CURRENT_KID, CURRENT_SECRET, Instant.now(), 30)));
    }

    @Test
    void rejectsExpiredFutureAndOversizedValidity() {
        assertUnauthorized(request("GET", PATH,
                sign(CURRENT_KID, CURRENT_SECRET, Instant.now().minusSeconds(120), 30)));
        assertUnauthorized(request("GET", PATH,
                sign(CURRENT_KID, CURRENT_SECRET, Instant.now().plusSeconds(120), 30)));
        assertUnauthorized(request("GET", PATH,
                sign(CURRENT_KID, CURRENT_SECRET, Instant.now(), 120)));
    }

    private static MockHttpServletRequest request(String method, String path, String assertion) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (assertion != null) {
            request.addHeader("X-Squad-Assertion", assertion);
        }
        return request;
    }

    private void assertUnauthorized(MockHttpServletRequest request) {
        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
    }

    private String sign(String kid, String secret, Instant now, long ttlSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "agent-squad-gateway");
        payload.put("aud", "agent-squad-server");
        payload.put("sub", USER_ID);
        payload.put("name", "tester");
        payload.put("roles", new ArrayList<>(List.of("USER")));
        payload.put("authVersion", 0);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(ttlSeconds).getEpochSecond());
        payload.put("method", "GET");
        payload.put("path", PATH);
        payload.put("requestId", "test");
        payload.put("kid", kid);
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String encoded = encoder.encodeToString(json.writeValueAsBytes(payload));
            return encoded + "." + encoder.encodeToString(hmac(secret, encoded));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
