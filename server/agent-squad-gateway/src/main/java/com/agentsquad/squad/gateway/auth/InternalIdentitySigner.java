package com.agentsquad.squad.gateway.auth;

import com.agentsquad.squad.vo.AuthVO;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 网关为每个受权业务请求签发短时 HMAC 断言。
 * 仅用于可信私网服务间传递主体，不是浏览器 JWT。
 */
@Component
public class InternalIdentitySigner {
    @Value("${agentsquad.identity.hmac-key:}")
    private String secret;
    @Value("${agentsquad.identity.key-id:local-v1}")
    private String keyId;
    @Value("${agentsquad.identity.strict:false}")
    private boolean strict;

    @PostConstruct
    public void validate() {
        if (strict && secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("生产内部断言密钥长度至少 32 字节");
    }

    public String sign(AuthVO actor, String method, String path) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            if (strict) throw new IllegalStateException("内部签名密钥缺失");
            return null;
        }
        long issued = Instant.now().getEpochSecond();
        long expires = issued + 30;
        String role = actor.roles().getFirst().name();
        String requestId = java.util.UUID.randomUUID().toString();
        String value = String.join("|", "agent-squad-gateway", "agent-squad-server", keyId,
                Long.toString(actor.userId()), actor.username(), role,
                Long.toString(issued), Long.toString(expires),
                method, path, requestId);
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return payload + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
