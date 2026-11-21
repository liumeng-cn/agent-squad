package com.agentsquad.squad.gateway.service;

import com.agentsquad.squad.common.enums.PermissionCode;
import org.junit.jupiter.api.Test;
import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 网关权限规则验证
 * <p>
 * 验证方法隔离、特殊选项接口和未声明路径的默认拒绝，防止写操作借用读取权限。
 */
class PermissionAuthorizationServiceTest {
    private final PermissionAuthorizationService service = new PermissionAuthorizationService();
    private final String prefix = "/user-server/api/v2/users/management";

    @Test
    void separatesReadsFromMutations() {
        assertEquals(PermissionCode.ROLE_READ, service.requiredPermission("GET", prefix + "/roles/abc"));
        assertEquals(PermissionCode.ROLE_MANAGE, service.requiredPermission("PUT", prefix + "/roles/abc"));
        assertEquals(PermissionCode.USER_MANAGE, service.requiredPermission("PUT", prefix + "/users/123/roles"));
        assertEquals(PermissionCode.PERMISSION_READ, service.requiredPermission("POST", prefix + "/permissions/page"));
        assertNull(service.requiredPermission("DELETE", prefix + "/roles/abc"));
    }

    @Test
    void matchesSpecialOptionsBeforeResourceDetails() {
        assertEquals(PermissionCode.ROLE_MANAGE, service.requiredPermission("GET", prefix + "/roles/permission-options"));
        assertEquals(PermissionCode.USER_MANAGE, service.requiredPermission("GET", prefix + "/users/role-options"));
    }

    @Test
    void rejectsUnknownPathsAndMethodsBeforeRemoteLookup() {
        assertNull(service.requiredPermission("GET", prefix + "/roles/abc/extra"));
        assertNull(service.requiredPermission("GET", prefix + "/permissions/page"));
        assertThrows(RuntimeException.class, () -> service.authorize(null, "POST", prefix + "/unknown").block());
        assertThrows(RuntimeException.class, () -> service.authorize(null, "GET", "/user-server/api/v2/users/%6danagement/roles/abc").block());
        assertThrows(RuntimeException.class, () -> service.authorize(null, "GET", prefix + ";x/roles/abc").block());
        service.authorize(null, "POST", "/v2-server/api/v2/chat/messages").block();
    }

    @Test
    void verifiesLivePermissionsAndSignedQuery() {
        AuthProperties properties = new AuthProperties();
        properties.setIdentityKey("test-key-for-rbac-permission-query-1234567890");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "userServerUrl", "http://user-service");
        AtomicReference<String> body = new AtomicReference<>("{\"code\":0,\"data\":{\"canAccess\":true,\"permissionCodes\":[\"management:role:read\"]}}");
        AtomicReference<String> signed = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            assertEquals("/user-server/api/v2/users/me/management-access", request.url().getPath());
            signed.set(request.headers().getFirst(IdentityAssertion.HEADER));
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body.get()).build());
        });
        ReflectionTestUtils.setField(service, "webClientBuilder", builder);
        CurrentUserBO user = new CurrentUserBO();
        user.setUserId("123");
        user.setSessionId("test-session");
        service.authorize(user, "GET", prefix + "/roles/test").block();
        assertEquals("123", IdentityAssertion.verify(signed.get(), "user-server", "GET",
                "/user-server/api/v2/users/me/management-access", properties.getIdentityKey()).getUserId());
        assertThrows(RuntimeException.class, () -> service.authorize(user, "PUT", prefix + "/roles/test").block());
        body.set("{\"code\":0,\"data\":{\"canAccess\":false,\"permissionCodes\":[]}}");
        assertThrows(RuntimeException.class, () -> service.authorize(user, "GET", prefix + "/roles/test").block());
        builder.exchangeFunction(request -> Mono.error(new IllegalStateException("unavailable")));
        assertThrows(RuntimeException.class, () -> service.authorize(user, "GET", prefix + "/roles/test").block());
        assertThrows(RuntimeException.class, () -> service.authorize(user, "GET", "/user-server/internal/users/123/auth").block());
    }
}
