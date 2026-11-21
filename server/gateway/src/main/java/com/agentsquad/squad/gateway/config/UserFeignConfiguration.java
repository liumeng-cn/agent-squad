package com.agentsquad.squad.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * 用户客户端独立配置
 * <p>
 * 仅注册到 Feign 子上下文，提供同步 JSON 编解码，不引入网关 WebFlux 过滤链。
 */
public class UserFeignConfiguration {
    @Bean
    public HttpMessageConverters userMessageConverters() {
        return new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
    }
}
