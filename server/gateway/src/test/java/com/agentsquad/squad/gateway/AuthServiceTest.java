package com.agentsquad.squad.gateway;

import com.agentsquad.squad.common.enums.UserStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.gateway.bo.LoginSessionBO;
import com.agentsquad.squad.gateway.client.UserAccountClient;
import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import com.agentsquad.squad.gateway.service.AuthService;
import com.agentsquad.squad.gateway.service.LoginSessionService;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 认证决策测试
 * <p>
 * 验证退出、CSRF与账号认证版本不能被有效JWT绕过。
 */
class AuthServiceTest {
    @Test
    void rejectsRevokedSessionWrongCsrfAndChangedVersion() {
        AuthService service = new AuthService();
        JwtDecoder decoder = mock(JwtDecoder.class);
        LoginSessionService sessions = mock(LoginSessionService.class);
        UserAccountClient users = mock(UserAccountClient.class);
        ReflectionTestUtils.setField(service, "properties", new AuthProperties());
        ReflectionTestUtils.setField(service, "decoder", decoder);
        ReflectionTestUtils.setField(service, "sessions", sessions);
        ReflectionTestUtils.setField(service, "users", users);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("100000").claim("jti", "session")
                .audience(List.of("squad-browser")).expiresAt(Instant.now().plusSeconds(600)).claim("authVersion", 0).build();
        when(decoder.decode("token")).thenReturn(jwt);
        when(sessions.read("session")).thenReturn(Mono.empty());
        assertThrows(BusinessException.class, () -> service.authenticate("token", false, null).block());
        LoginSessionBO session = new LoginSessionBO();
        session.setSessionId("session");
        session.setUserId("100000");
        session.setCsrfToken("csrf");
        session.setExpiresAt(Instant.now().plusSeconds(600));
        when(sessions.read("session")).thenReturn(Mono.just(session));
        assertThrows(BusinessException.class, () -> service.authenticate("token", true, "wrong").block());
        UserAccountVO user = new UserAccountVO();
        user.setUserId("100000");
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthVersion(1);
        when(users.byId("100000")).thenReturn(Mono.just(user));
        assertThrows(BusinessException.class, () -> service.authenticate("token", false, null).block());
        user.setAuthVersion(0);
        assertEquals("100000", service.authenticate("token", true, "csrf").block().getUser().getUserId());
    }
}
