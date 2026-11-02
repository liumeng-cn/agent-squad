package com.agentsquad.squad.identity;

import org.junit.jupiter.api.Test;
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
 * 网关内部身份断言的签名、时效与请求绑定验证。
 *
 * 只构造本地 HMAC 断言，不连接网关或外部服务。
 */
class SignedIdentityVerifierTest {

    private static final String CURRENT_KEY = "current-key-0123456789-0123456789-ok";
    private static final String PREVIOUS_KEY = "previous-key-0123456789-0123456789-ok";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void acceptsValidAssertion() {
        SignedIdentityVerifier verifier = verifier(CURRENT_KEY, "k1", "", "", true);
        MockHttpServletRequest request = request("GET", "/api/v1/conversations");
        request.addHeader("X-Squad-Assertion", assertion(CURRENT_KEY, "k1", USER_ID, "USER",
                "GET", "/api/v1/conversations", Instant.now().getEpochSecond()));

        AuthView view = verifier.verify(request);

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.username()).isEqualTo("alice");
        assertThat(view.roles()).containsExactly("USER");

        MockHttpServletRequest operatorRequest = request("GET", "/api/v1/conversations");
        operatorRequest.addHeader("X-Squad-Assertion", assertion(CURRENT_KEY, "k1", USER_ID, "OPERATOR",
                "GET", "/api/v1/conversations", Instant.now().getEpochSecond()));
        assertThat(verifier.verify(operatorRequest).roles()).containsExactly("OPERATOR");
    }

    @Test
    void rejectsAssertionForAnotherRequest() {
        SignedIdentityVerifier verifier = verifier(CURRENT_KEY, "k1", "", "", true);
        String assertion = assertion(CURRENT_KEY, "k1", USER_ID, "USER", "GET", "/api/v1/conversations",
                Instant.now().getEpochSecond());
        MockHttpServletRequest request = request("POST", "/api/v1/conversations");
        request.addHeader("X-Squad-Assertion", assertion);

        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void rejectsModifiedPayloadAndSignature() {
        SignedIdentityVerifier verifier = verifier(CURRENT_KEY, "k1", "", "", true);
        String assertion = assertion(CURRENT_KEY, "k1", USER_ID, "USER", "GET", "/api/v1/conversations",
                Instant.now().getEpochSecond());
        String encoded = assertion.substring(0, assertion.indexOf('.'));
        String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
                .replace("alice", "mallory");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(decoded.getBytes(StandardCharsets.UTF_8));
        String tampered = tamperedPayload + assertion.substring(assertion.indexOf('.'));
        MockHttpServletRequest request = request("GET", "/api/v1/conversations");
        request.addHeader("X-Squad-Assertion", tampered);

        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsExpiredFutureAndOverlongAssertions() {
        SignedIdentityVerifier verifier = verifier(CURRENT_KEY, "k1", "", "", true);
        long now = Instant.now().getEpochSecond();

        assertRejected(verifier, assertion(CURRENT_KEY, "k1", USER_ID, "USER", "GET",
                "/api/v1/conversations", now - 60));
        assertRejected(verifier, assertion(CURRENT_KEY, "k1", USER_ID, "USER", "GET",
                "/api/v1/conversations", now + 60));
        assertRejected(verifier, assertionWithWindow(CURRENT_KEY, "k1", USER_ID, "USER", "GET",
                "/api/v1/conversations", now, 31));
    }

    @Test
    void rejectsInvalidSubjectFields() {
        SignedIdentityVerifier verifier = verifier(CURRENT_KEY, "k1", "", "", true);
        long now = Instant.now().getEpochSecond();

        assertRejected(verifier, assertion(CURRENT_KEY, "k1", "not-a-uuid", "USER", "GET",
                "/api/v1/conversations", now));
        assertRejected(verifier, assertion(CURRENT_KEY, "k1", USER_ID, "ADMIN,USER", "GET",
                "/api/v1/conversations", now));
        assertRejected(verifier, assertion(CURRENT_KEY, "k1", USER_ID, "", "GET",
                "/api/v1/conversations", now));
    }

    @Test
    void strictModeRejectsMissingAssertionWhileLocalModeReturnsNull() {
        SignedIdentityVerifier strict = verifier(CURRENT_KEY, "k1", "", "", true);
        SignedIdentityVerifier local = verifier(CURRENT_KEY, "k1", "", "", false);
        MockHttpServletRequest request = request("GET", "/api/v1/conversations");

        assertThatThrownBy(() -> strict.verify(request)).isInstanceOf(ResponseStatusException.class);
        assertThat(local.verify(request)).isNull();
    }

    @Test
    void validatesKeyRotationConfiguration() {
        assertThatThrownBy(() -> verifier("short", "k1", "", "", true).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> verifier(CURRENT_KEY, "k1", PREVIOUS_KEY, "", true).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> verifier(CURRENT_KEY, "k1", PREVIOUS_KEY, "k1", true).validate())
                .isInstanceOf(IllegalStateException.class);

        SignedIdentityVerifier rotated = verifier(CURRENT_KEY, "k2", PREVIOUS_KEY, "k1", true);
        rotated.validate();
        MockHttpServletRequest request = request("GET", "/api/v1/conversations");
        request.addHeader("X-Squad-Assertion", assertion(PREVIOUS_KEY, "k1", USER_ID, "USER", "GET",
                "/api/v1/conversations", Instant.now().getEpochSecond()));
        assertThat(rotated.verify(request).userId()).isEqualTo(USER_ID);
    }

    private void assertRejected(SignedIdentityVerifier verifier, String assertion) {
        MockHttpServletRequest request = request("GET", "/api/v1/conversations");
        request.addHeader("X-Squad-Assertion", assertion);
        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(ResponseStatusException.class);
    }

    private SignedIdentityVerifier verifier(String currentKey, String currentKid,
                                            String previousKey, String previousKid, boolean strict) {
        SignedIdentityVerifier verifier = new SignedIdentityVerifier();
        ReflectionTestUtils.setField(verifier, "currentKey", currentKey);
        ReflectionTestUtils.setField(verifier, "currentKid", currentKid);
        ReflectionTestUtils.setField(verifier, "previousKey", previousKey);
        ReflectionTestUtils.setField(verifier, "previousKid", previousKid);
        ReflectionTestUtils.setField(verifier, "strict", strict);
        return verifier;
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private String assertion(String key, String kid, String userId, String role,
                             String method, String path, long issuedAt) {
        return assertionWithWindow(key, kid, userId, role, method, path, issuedAt, 20);
    }

    private String assertionWithWindow(String key, String kid, String userId, String role,
                                       String method, String path, long issuedAt, long ttlSeconds) {
        String payload = String.join("|",
                "agent-squad-gateway",
                "agent-squad-server",
                kid,
                userId,
                "alice",
                role,
                Long.toString(issuedAt),
                Long.toString(issuedAt + ttlSeconds),
                method,
                path,
                UUID.randomUUID().toString());
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(key, encoded);
    }

    private String sign(String key, String encoded) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
