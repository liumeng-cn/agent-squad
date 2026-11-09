package com.agentsquad.squad.identity;

import com.agentsquad.squad.enums.AccountRole;
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
import java.util.List;

/** 业务入口只接受可信网关签发的有限时效身份事实。 */
@Component
public class SignedIdentityVerifier {
    @Value("${agentsquad.identity.hmac-key:}") private String currentKey;
    @Value("${agentsquad.identity.key-id:local-v1}") private String currentKid;
    @Value("${agentsquad.identity.previous-hmac-key:}") private String previousKey;
    @Value("${agentsquad.identity.previous-key-id:}") private String previousKid;
    @Value("${agentsquad.identity.strict:false}") private boolean strict;

    @PostConstruct
    public void validate() {
        if(strict && currentKey.getBytes(StandardCharsets.UTF_8).length<32)
            throw new IllegalStateException("生产服务必须配置 32 字节以上的断言 HMAC 密钥");
    }

    public AuthView verify(HttpServletRequest request) {
        var assertions=java.util.Collections.list(request.getHeaders("X-Squad-Assertion"));
        if(assertions.isEmpty()) {
            if(strict)throw invalid();
            return null;
        }
        if(assertions.size()!=1)throw invalid();
        String value=assertions.getFirst();
        if(value==null || value.length()>4096)throw invalid();
        int dot=value.indexOf('.');
        if(dot<1||dot!=value.lastIndexOf('.'))throw invalid();
        try {
            String encoded=value.substring(0,dot), signature=value.substring(dot+1);
            String body=new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8);
            String[] parts=body.split("\\|",-1);
            if(parts.length!=11)throw invalid();
            if(!"agent-squad-gateway".equals(parts[0]) || !"agent-squad-server".equals(parts[1]))throw invalid();
            String secret=parts[2].equals(currentKid)?currentKey:
                    (parts[2].equals(previousKid)?previousKey:"");
            if(secret.getBytes(StandardCharsets.UTF_8).length<32)throw invalid();
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII));
            if(!MessageDigest.isEqual(expected,Base64.getUrlDecoder().decode(signature)))throw invalid();
            long issued=Long.parseLong(parts[6]),expiry=Long.parseLong(parts[7]),now=Instant.now().getEpochSecond();
            if(issued>now+5||issued<now-35||expiry<now-5||expiry>issued+30)throw invalid();
            if(!parts[8].equals(request.getMethod())||!parts[9].equals(request.getRequestURI()))throw invalid();
            if(!parts[3].matches("[0-9a-fA-F-]{36}"))throw invalid();
            AccountRole role;
            try {
                role = AccountRole.valueOf(parts[5]);
            } catch (IllegalArgumentException exception) {
                throw invalid();
            }
            if(!parts[10].matches("[0-9a-fA-F-]{36}"))throw invalid();
            return new AuthView(parts[3],parts[4],List.of(role),null);
        }catch(IllegalArgumentException|java.security.GeneralSecurityException ex) {
            throw invalid();
        }
    }
    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"网关身份断言无效");
    }
}
