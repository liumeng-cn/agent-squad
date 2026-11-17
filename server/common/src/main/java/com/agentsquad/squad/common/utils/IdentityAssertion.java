package com.agentsquad.squad.common.utils;

import com.agentsquad.squad.common.bo.CurrentUserBO;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 内部服务身份签名协议
 * <p>
 * 所属common模块，按明确职责维护用户身份与数据边界。
 */
public final class IdentityAssertion {
    public static final String HEADER = "X-Squad-Identity";
    public static final String ATTRIBUTE = "squad.currentUser";

    private IdentityAssertion() {
    }

    /**
     * 短期断言绑定目标服务与请求，禁止直接信任浏览器用户头。
     */
    public static String sign(CurrentUserBO user, String audience, String method, String path, String secret) {
        String text = String.join("|", user.getUserId(), user.getSessionId(), audience, method, path,
                Long.toString(Instant.now().getEpochSecond() + 30));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(payload, secret));
    }

    public static CurrentUserBO verify(String token, String audience, String method, String path, String secret) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(mac(parts[0], secret), Base64.getUrlDecoder().decode(parts[1]))) {
                throw new IllegalArgumentException("身份签名无效");
            }
            String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
            long now = Instant.now().getEpochSecond();
            if (fields.length != 6 || !fields[0].matches("[1-9][0-9]{0,18}") || !fields[2].equals(audience)
                    || !fields[3].equals(method) || !fields[4].equals(path)
                    || Long.parseLong(fields[5]) <= now || Long.parseLong(fields[5]) > now + 35) {
                throw new IllegalArgumentException("身份断言无效");
            }
            CurrentUserBO user = new CurrentUserBO();
            user.setUserId(fields[0]);
            user.setSessionId(fields[1]);
            return user;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("缺少有效内部身份", exception);
        }
    }

    private static byte[] mac(String text, String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("内部身份密钥至少32字符");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("身份签名失败", exception);
        }
    }
}
