package com.agentsquad.squad.gateway.client;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.user.api.client.UserClient;
import com.agentsquad.squad.user.api.dto.PermissionCheckDTO;
import feign.InvocationHandlerFactory;
import feign.Target;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cloud.openfeign.CachingCapability;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 原生 Feign 缓存验证
 * <p>
 * 使用官方 CachingCapability 执行接口上的缓存注解，验证命中后不再发起远程请求。
 */
class NativeFeignPermissionCacheTest {
    @Test
    void nativeCapabilityCachesAnnotatedPermissionMethod() throws Exception {
        CacheInterceptor interceptor = new CacheInterceptor();
        interceptor.setCacheOperationSources(new AnnotationCacheOperationSource());
        interceptor.setCacheManager(new ConcurrentMapCacheManager("permissionChecks"));
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("permissionCacheKeyGenerator", (KeyGenerator)
                (target, method, params) -> ((PermissionCheckDTO) params[0]).getUserId());
        interceptor.setBeanFactory(beans);
        interceptor.afterPropertiesSet();
        interceptor.afterSingletonsInstantiated();
        AtomicInteger calls = new AtomicInteger();
        InvocationHandlerFactory factory = (target, dispatch) -> (proxy, method, args) -> {
            calls.incrementAndGet();
            return ApiResult.success(false);
        };
        var decorated = new CachingCapability(interceptor).enrich(factory);
        var target = new Target.HardCodedTarget<>(UserClient.class, "http://user-service");
        var handler = decorated.create(target, Map.of());
        UserClient client = (UserClient) Proxy.newProxyInstance(UserClient.class.getClassLoader(), new Class<?>[]{UserClient.class}, handler);
        PermissionCheckDTO request = new PermissionCheckDTO();
        request.setUserId("123");
        request.setMethod("POST");
        request.setPath("/user-server/api/v2/users/management/roles/page");
        assertEquals(Boolean.FALSE, client.checkPermission(request).getData());
        assertEquals(Boolean.FALSE, client.checkPermission(request).getData());
        assertEquals(1, calls.get());
        request.setUserId("456");
        client.checkPermission(request);
        assertEquals(2, calls.get());
    }
}
