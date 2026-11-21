package com.agentsquad.squad.gateway.client;

import com.agentsquad.squad.user.api.dto.PermissionCheckDTO;
import jakarta.annotation.Resource;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 鉴权缓存版本键
 * <p>
 * 缓存按授权版本、用户、方法与路径隔离；用户服务在授权事务提交后推进共享版本。
 */
@Component
public class PermissionCacheKeyGenerator implements KeyGenerator {
    @Resource
    private StringRedisTemplate redis;

    @Override
    public Object generate(Object target, Method method, Object... params) {
        PermissionCheckDTO request = (PermissionCheckDTO) params[0];
        String version = redis.opsForValue().get("squad:rbac:permission-version");
        return new SimpleKey(version == null ? "0" : version, request.getUserId(), request.getMethod(), request.getPath());
    }
}
