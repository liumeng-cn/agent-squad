package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.agent.observer.AgentTracingMiddleware;
import com.agentsquad.squad.v2.agent.observer.TraceContextSpanProcessor;
import com.agentsquad.squad.v2.config.properties.ObservationProperties;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * OTLP 观测装配
 * <p>
 * 应用独立持有 OTel SDK，不替换进程全局实例；导出故障不参与聊天事务或执行结果判定。
 */
@Configuration
@EnableConfigurationProperties(ObservationProperties.class)
@ConditionalOnProperty(prefix = "agentsquad.v2.observation", name = "enabled", havingValue = "true")
public class ObservationConfiguration {
    @jakarta.annotation.Resource
    private ObservationProperties properties;

    /**
     * 初始化有界异步导出队列；应用关闭时由 SDK 刷新并关闭导出器。
     */
    @Bean(destroyMethod = "close")
    public OpenTelemetrySdk observationSdk() {
        var builder = OtlpHttpSpanExporter.builder()
                .setEndpoint(properties.getEndpoint())
                .setTimeout(Duration.ofSeconds(2));
        if (properties.getPublicKey() != null && !properties.getPublicKey().isBlank()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            String credentials = properties.getPublicKey() + ":" + properties.getSecretKey();
            builder.addHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        var exporter = builder.build();
        var processor = BatchSpanProcessor.builder(exporter)
                .setMaxQueueSize(2048).setMaxExportBatchSize(512)
                .setScheduleDelay(Duration.ofSeconds(1)).build();
        var provider = SdkTracerProvider.builder()
                .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "agent-squad-v2")))
                .addSpanProcessor(new TraceContextSpanProcessor())
                .addSpanProcessor(processor).build();
        return OpenTelemetrySdk.builder().setTracerProvider(provider).build();
    }

    /**
     * 复用原生生命周期埋点，并补充 Langfuse 与 AgentScope 官方字段。
     */
    @Bean
    public AgentTracingMiddleware agentTracingMiddleware(OpenTelemetrySdk observationSdk) {
        return new AgentTracingMiddleware(observationSdk, properties.getEnvironment());
    }
}
