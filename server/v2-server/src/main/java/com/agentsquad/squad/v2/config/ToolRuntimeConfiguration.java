package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.config.properties.CredentialProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 配置化工具运行配置
 * <p>
 * 绑定外部调用凭据，并提供 HTTP 工具共享的客户端；客户端不跟随重定向，避免请求被转到未配置的地址。
 */
@Configuration
@EnableConfigurationProperties(CredentialProperties.class)
public class ToolRuntimeConfiguration {
    /**
     * 创建HTTP工具客户端
     */
    @Bean
    public HttpClient toolHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
