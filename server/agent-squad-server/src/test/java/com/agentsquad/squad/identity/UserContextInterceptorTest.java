package com.agentsquad.squad.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import com.agentsquad.squad.common.identity.AccountRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务服务身份接入边界验证。
 *
 * 覆盖签名优先、严格模式拒绝未签名头、本地兼容开关和本地身份格式校验。
 */
class UserContextInterceptorTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void signedAssertionTakesPrecedenceAndChecksUserStatus() {
        SignedIdentityVerifier verifier = mock(SignedIdentityVerifier.class);
        UserStatusGuard status = mock(UserStatusGuard.class);
        AuthView view = new AuthView(USER_ID, "alice", List.of(AccountRole.USER), null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/conversations");
        when(verifier.verify(request)).thenReturn(view);
        UserContextInterceptor interceptor = interceptor(verifier, status, false, true);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(status).requireActive(USER_ID);
        assertThat(request.getAttribute(UserContext.ATTRIBUTE)).isEqualTo(view);
    }

    @Test
    void strictModeRejectsUnsignedHeadersEvenWhenLocalCompatibilityEnabled() {
        SignedIdentityVerifier verifier = mock(SignedIdentityVerifier.class);
        UserStatusGuard status = mock(UserStatusGuard.class);
        MockHttpServletRequest request = unsignedRequest(USER_ID, "USER");
        when(verifier.verify(request)).thenReturn(null);
        UserContextInterceptor interceptor = interceptor(verifier, status, true, true);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void localCompatibilityRejectsInvalidHeaders() {
        SignedIdentityVerifier verifier = mock(SignedIdentityVerifier.class);
        UserStatusGuard status = mock(UserStatusGuard.class);
        UserContextInterceptor interceptor = interceptor(verifier, status, false, true);

        assertUnauthorized(interceptor, unsignedRequest("not-a-uuid", "USER"));
        assertUnauthorized(interceptor, unsignedRequest(USER_ID, "ADMIN,USER"));
        assertUnauthorized(interceptor, unsignedRequest(USER_ID, ""));
    }

    @Test
    void localCompatibilityDisabledRejectsUnsignedHeaders() {
        SignedIdentityVerifier verifier = mock(SignedIdentityVerifier.class);
        UserStatusGuard status = mock(UserStatusGuard.class);
        UserContextInterceptor interceptor = interceptor(verifier, status, false, false);

        assertUnauthorized(interceptor, unsignedRequest(USER_ID, "USER"));
    }

    @Test
    void localCompatibilityAcceptsOperatorRole() {
        SignedIdentityVerifier verifier = mock(SignedIdentityVerifier.class);
        UserStatusGuard status = mock(UserStatusGuard.class);
        MockHttpServletRequest request = unsignedRequest(USER_ID, "OPERATOR");
        UserContextInterceptor interceptor = interceptor(verifier, status, false, true);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(((AuthView) request.getAttribute(UserContext.ATTRIBUTE)).roles())
                .containsExactly(AccountRole.OPERATOR);
    }

    @Test
    void localCompatibilityAcceptsValidUnsignedIdentity() {
        SignedIdentityVerifier verifier = mock(SignedIdentityVerifier.class);
        UserStatusGuard status = mock(UserStatusGuard.class);
        MockHttpServletRequest request = unsignedRequest(USER_ID, "USER");
        request.addHeader("X-User-Name", "local-user");
        UserContextInterceptor interceptor = interceptor(verifier, status, false, true);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(status).requireActive(USER_ID);
        assertThat(((AuthView) request.getAttribute(UserContext.ATTRIBUTE)).username())
                .isEqualTo("local-user");
    }

    private void assertUnauthorized(UserContextInterceptor interceptor, MockHttpServletRequest request) {
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(401));
    }

    private MockHttpServletRequest unsignedRequest(String userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/conversations");
        request.addHeader("X-User-Id", userId);
        request.addHeader("X-User-Role", role);
        return request;
    }

    private UserContextInterceptor interceptor(SignedIdentityVerifier verifier, UserStatusGuard status,
                                               boolean strict, boolean allowUnsignedLocal) {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        ReflectionTestUtils.setField(interceptor, "verifier", verifier);
        ReflectionTestUtils.setField(interceptor, "userStatus", status);
        ReflectionTestUtils.setField(interceptor, "strict", strict);
        ReflectionTestUtils.setField(interceptor, "allowUnsignedLocal", allowUnsignedLocal);
        return interceptor;
    }
}
