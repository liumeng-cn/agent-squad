package com.agentsquad.squad.gateway.assertion;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关到业务服务的短时 HMAC 身份断言签发器。
 *
 * <p>断言覆盖用户、角色、授权代际与本次请求的方法/原始路径，业务端只信任该签名，
 * 不接受浏览器直接写入的身份头。密钥不足 256 bit 时启动失败。</p>
 */
@Component
public class AssertionSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

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
     * 生成 `base64url(payload).base64url(HMAC-SHA256(payload))` 断言。
     */
    public String sign(String userId, String username, List<String> roles, long authVersion,
                       String method, String path, String requestId, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", properties.getIssuer());
        payload.put("aud", properties.getAudience());
        payload.put("sub", userId);
        payload.put("name", username);
        payload.put("roles", roles);
        payload.put("authVersion", authVersion);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(properties.getTtlSeconds()).getEpochSecond());
        payload.put("method", method);
        payload.put("path", path);
        payload.put("requestId", requestId == null ? "" : requestId);
        payload.put("kid", properties.getCurrentKid());
        try {
            String encoded = encoder().encodeToString(objectMapper.writeValueAsBytes(payload));
            return encoded + "." + encoder().encodeToString(hmac(currentKey, encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("内部身份断言签发失败", exception);
        }
    }

    static byte[] hmac(byte[] key, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 不可用", exception);
        }
    }

    private static Base64.Encoder encoder() {
        return Base64.getUrlEncoder().withoutPadding();
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
