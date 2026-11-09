package com.agentsquad.squad.identity;

import com.agentsquad.squad.enums.AccountRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 业务入口的网关身份断言校验。
 *
 * <p>覆盖签名、密钥版本、方法/路径与时间窗；任何篡改、过期或缺失都必须拒绝，
 * 不允许回退到浏览器可伪造的裸身份头。</p>
 */
class SignedIdentityVerifierTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String CURRENT_KID = "k-test";
    private static final String CURRENT_SECRET = "test-only-assertion-secret-0123456789abcdef";
    private static final String PREVIOUS_KID = "k-prev";
    private static final String PREVIOUS_SECRET = "test-only-previous-secret-0123456789abcdef";
    private static final String PATH = "/api/v1/conversations";
    private static final String HEADER = "X-Squad-Assertion";

    private SignedIdentityVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new SignedIdentityVerifier();
        ReflectionTestUtils.setField(verifier, "currentKey", CURRENT_SECRET);
        ReflectionTestUtils.setField(verifier, "currentKid", CURRENT_KID);
        ReflectionTestUtils.setField(verifier, "previousKey", PREVIOUS_SECRET);
        ReflectionTestUtils.setField(verifier, "previousKid", PREVIOUS_KID);
        ReflectionTestUtils.setField(verifier, "strict", false);
    }

    @Test
    void acceptsValidAssertionIncludingOperatorRole() {
        long now = Instant.now().getEpochSecond();
        MockHttpServletRequest request = request("GET", PATH);
        request.addHeader(HEADER, assertion(CURRENT_KID, CURRENT_SECRET, "OPERATOR", "GET", PATH, now, now + 30));

        AuthView actor = verifier.verify(request);

        assertThat(actor).isNotNull();
        assertThat(actor.userId()).isEqualTo(USER_ID);
        assertThat(actor.roles()).containsExactly(AccountRole.OPERATOR);
    }

    @Test
    void acceptsPreviousKidDuringRotation() {
        long now = Instant.now().getEpochSecond();
        MockHttpServletRequest request = request("GET", PATH);
        request.addHeader(HEADER, assertion(PREVIOUS_KID, PREVIOUS_SECRET, "USER", "GET", PATH, now, now + 30));

        assertThat(verifier.verify(request).roles()).containsExactly(AccountRole.USER);
    }

    @Test
    void rejectsTamperedPayload() {
        long now = Instant.now().getEpochSecond();
        String original = assertion(CURRENT_KID, CURRENT_SECRET, "USER", "GET", PATH, now, now + 30);
        String encoded = original.substring(0, original.indexOf('.'));
        String body = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
                .replace(USER_ID, UUID.randomUUID().toString());
        String tampered = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8))
                + original.substring(original.indexOf('.'));
        MockHttpServletRequest request = request("GET", PATH);
        request.addHeader(HEADER, tampered);

        assertUnauthorized(request);
    }

    @Test
    void rejectsUnknownKid() {
        long now = Instant.now().getEpochSecond();
        MockHttpServletRequest request = request("GET", PATH);
        request.addHeader(HEADER, assertion("k-unknown", CURRENT_SECRET, "USER", "GET", PATH, now, now + 30));

        assertUnauthorized(request);
    }

    @Test
    void rejectsMethodOrPathMismatch() {
        long now = Instant.now().getEpochSecond();
        MockHttpServletRequest otherPath = request("GET", "/api/v1/tasks");
        otherPath.addHeader(HEADER, assertion(CURRENT_KID, CURRENT_SECRET, "USER", "GET", PATH, now, now + 30));
        assertUnauthorized(otherPath);

        MockHttpServletRequest otherMethod = request("POST", PATH);
        otherMethod.addHeader(HEADER, assertion(CURRENT_KID, CURRENT_SECRET, "USER", "GET", PATH, now, now + 30));
        assertUnauthorized(otherMethod);
    }

    @Test
    void rejectsExpiredOrOverlongAssertion() {
        long now = Instant.now().getEpochSecond();
        MockHttpServletRequest expired = request("GET", PATH);
        expired.addHeader(HEADER,
                assertion(CURRENT_KID, CURRENT_SECRET, "USER", "GET", PATH, now - 40, now - 10));
        assertUnauthorized(expired);

        MockHttpServletRequest overlong = request("GET", PATH);
        overlong.addHeader(HEADER,
                assertion(CURRENT_KID, CURRENT_SECRET, "USER", "GET", PATH, now, now + 60));
        assertUnauthorized(overlong);
    }

    @Test
    void rejectsDuplicateHeader() {
        long now = Instant.now().getEpochSecond();
        String value = assertion(CURRENT_KID, CURRENT_SECRET, "USER", "GET", PATH, now, now + 30);
        MockHttpServletRequest request = request("GET", PATH);
        request.addHeader(HEADER, value);
        request.addHeader(HEADER, value);

        assertUnauthorized(request);
    }

    @Test
    void missingHeaderOnlyAllowedOutsideStrictMode() {
        MockHttpServletRequest request = request("GET", PATH);
        assertThat(verifier.verify(request)).isNull();

        ReflectionTestUtils.setField(verifier, "strict", true);
        assertUnauthorized(request);
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private void assertUnauthorized(MockHttpServletRequest request) {
        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static String assertion(String kid, String secret, String role, String method, String path,
                                    long issued, long expiry) {
        String value = String.join("|", "agent-squad-gateway", "agent-squad-server", kid,
                USER_ID, USER_ID, role, Long.toString(issued), Long.toString(expiry),
                method, path, UUID.randomUUID().toString());
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encoded = encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + encoder.encodeToString(hmac(secret, encoded));
    }

    private static byte[] hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 不可用", exception);
        }
    }
}
