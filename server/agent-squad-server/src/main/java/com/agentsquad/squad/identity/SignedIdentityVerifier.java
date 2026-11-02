package com.agentsquad.squad.identity;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 内部网关身份断言验证器。
 *
 * 验证网关身份断言的签名、有效时间、请求绑定与主体格式，
 * 不从客户端提供的普通身份头推导用户身份。
 */
@Component
public class SignedIdentityVerifier {

    private static final String ISSUER = "agent-squad-gateway";
    private static final String AUDIENCE = "agent-squad-server";
    private static final long MAX_ASSERTION_SECONDS = 30;

    @Value("${agentsquad.identity.hmac-key:}")
    private String currentKey;

    @Value("${agentsquad.identity.key-id:local-v1}")
    private String currentKid;

    @Value("${agentsquad.identity.previous-hmac-key:}")
    private String previousKey;

    @Value("${agentsquad.identity.previous-key-id:}")
    private String previousKid;

    @Value("${agentsquad.identity.strict:false}")
    private boolean strict;

    @PostConstruct
    public void validate() {
        if (strict && currentKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("生产服务必须配置 32 字节以上的断言 HMAC 密钥");
        }
        if (!currentKid.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalStateException("内部断言当前密钥标识不合法");
        }
        if (previousKey.isBlank() != previousKid.isBlank()) {
            throw new IllegalStateException("上一代内部断言密钥与密钥标识必须同时配置");
        }
        if (!previousKey.isBlank()) {
            if (previousKey.getBytes(StandardCharsets.UTF_8).length < 32
                    || !previousKid.matches("[A-Za-z0-9._-]{1,64}")
                    || currentKid.equals(previousKid)) {
                throw new IllegalStateException("上一代内部断言密钥配置不合法");
            }
        }
    }

    public AuthView verify(HttpServletRequest request) {
        List<String> assertions = Collections.list(request.getHeaders("X-Squad-Assertion"));
        if (assertions.isEmpty()) {
            if (strict) {
                throw invalid();
            }
            return null;
        }
        if (assertions.size() != 1) {
            throw invalid();
        }

        String assertion = assertions.getFirst();
        if (assertion == null || assertion.isBlank() || assertion.length() > 4096) {
            throw invalid();
        }

        int dot = assertion.indexOf('.');
        if (dot < 1 || dot != assertion.lastIndexOf('.') || dot == assertion.length() - 1) {
            throw invalid();
        }

        try {
            String encoded = assertion.substring(0, dot);
            String signature = assertion.substring(dot + 1);
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", -1);

            // 主体断言仅授权单一业务请求，不能作为可转发的长期登录凭据。
            if (fields.length != 11 || !ISSUER.equals(fields[0]) || !AUDIENCE.equals(fields[1])) {
                throw invalid();
            }
            verifySignature(fields[2], encoded, signature);
            verifyLifetime(fields[6], fields[7]);
            verifyRequest(request, fields[8], fields[9]);

            String userId = UUID.fromString(fields[3]).toString();
            String requestId = UUID.fromString(fields[10]).toString();
            if (!userId.equalsIgnoreCase(fields[3]) || !requestId.equalsIgnoreCase(fields[10])
                    || fields[4].isBlank() || fields[4].length() > 64
                    || !List.of("ADMIN", "USER", "OPERATOR").contains(fields[5])) {
                throw invalid();
            }
            return new AuthView(userId, fields[4], List.of(fields[5]), null);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw invalid();
        }
    }

    private void verifySignature(String keyId, String encoded, String signature)
            throws java.security.GeneralSecurityException {
        String secret = "";
        if (currentKid.equals(keyId)) {
            secret = currentKey;
        } else if (!previousKid.isBlank() && previousKid.equals(keyId)) {
            secret = previousKey;
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw invalid();
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII));
        byte[] provided = Base64.getUrlDecoder().decode(signature);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw invalid();
        }
    }

    private void verifyLifetime(String issuedAt, String expiresAt) {
        long issued = Long.parseLong(issuedAt);
        long expires = Long.parseLong(expiresAt);
        long now = Instant.now().getEpochSecond();
        if (issued > now + 5 || issued < now - 35 || expires <= now
                || expires <= issued || expires - issued > MAX_ASSERTION_SECONDS) {
            throw invalid();
        }
    }

    private void verifyRequest(HttpServletRequest request, String method, String path) {
        if (!request.getMethod().equals(method) || !request.getRequestURI().equals(path)) {
            throw invalid();
        }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "网关身份断言无效");
    }
}
