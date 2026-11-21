package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.user.server.observer.AuthorizationChangedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * 权限缓存失效通知
 * <p>
 * 授权事务提交后推进 Redis 共享版本，通知 Feign 调用侧缓存失效，不缓存鉴权查询结果。
 */
@Service
@Slf4j
public class PermissionCacheInvalidationService {
    private static final String VERSION_KEY = "squad:rbac:permission-version";

    @Resource
    private StringRedisTemplate redis;

    /**
     * 提交后失效授权
     */
    @TransactionalEventListener
    public void invalidate(AuthorizationChangedEvent event) {
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (RuntimeException exception) {
            log.warn("授权缓存版本更新失败，旧结果将在短时有效期内失效", exception);
        }
    }
}
