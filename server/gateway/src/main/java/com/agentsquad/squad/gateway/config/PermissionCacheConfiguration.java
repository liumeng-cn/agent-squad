package com.agentsquad.squad.gateway.config;

import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import jakarta.annotation.Resource;

import java.util.Set;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Feign 原生鉴权缓存配置
 * <p>
 * 启用 OpenFeign 的 Spring Cache 能力，Redis 缓存成功的鉴权响应，期限由网关配置控制。
 */
@Configuration
@EnableCaching
public class PermissionCacheConfiguration {
    @Resource
    private RedisConnectionFactory connectionFactory;

    @Resource
    private AuthProperties properties;

    @Bean
    public CacheManager cacheManager() {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.getPermissionCacheTtl())
                .disableCachingNullValues()
                .computePrefixWith(name -> "squad:rbac:feign:" + name + ":")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .initialCacheNames(Set.of("permissionChecks"))
                .build();
    }
}
