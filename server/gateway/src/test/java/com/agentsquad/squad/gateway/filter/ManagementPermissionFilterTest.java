package com.agentsquad.squad.gateway.filter;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.user.api.client.UserClient;
import com.agentsquad.squad.user.api.dto.PermissionCheckDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 路由鉴权委托测试
 * <p>
 * Gateway 只转交已认证用户和目标请求，按用户服务结果放行；拒绝与查询故障不能进入下游。
 */
class ManagementPermissionFilterTest {
    @Test
    void delegatesPermissionDecisionAndFailsClosed() {
        UserClient users = mock(UserClient.class);
        PermissionFilterFactory factory = new PermissionFilterFactory();
        ReflectionTestUtils.setField(factory, "users", users);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/user-server/api/v2/users/management/roles/test"));
        CurrentUserBO actor = new CurrentUserBO();
        actor.setUserId("123");
        actor.setSessionId("test");
        exchange.getAttributes().put(IdentityAssertion.ATTRIBUTE, actor);
        AtomicBoolean forwarded = new AtomicBoolean();
        when(users.checkPermission(any())).thenAnswer(invocation -> {
            PermissionCheckDTO target = invocation.getArgument(0);
            assertEquals("123", target.getUserId());
            assertEquals("GET", target.getMethod());
            assertEquals(exchange.getRequest().getURI().getRawPath(), target.getPath());
            return ApiResult.success(true);
        });
        var filter = factory.apply(new Object());
        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();
        assertTrue(forwarded.get());
        forwarded.set(false);
        doReturn(ApiResult.success(false)).when(users).checkPermission(any());
        assertThrows(RuntimeException.class, () -> filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block());
        assertFalse(forwarded.get());
        doThrow(new IllegalStateException("unavailable")).when(users).checkPermission(any());
        assertThrows(RuntimeException.class, () -> filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block());
        assertFalse(forwarded.get());
    }
}
