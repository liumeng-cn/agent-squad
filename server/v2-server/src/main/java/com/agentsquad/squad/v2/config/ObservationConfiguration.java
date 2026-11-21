package com.agentsquad.squad.v2.config;

import com.agentsquad.squad.v2.agent.tracing.FrameworkTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.HttpTracingFilter;
import com.agentsquad.squad.v2.agent.tracing.LangfuseTraceSerializer;
import com.agentsquad.squad.v2.agent.tracing.ModelIoTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ReasoningTracingMiddleware;
import com.agentsquad.squad.v2.agent.tracing.ToolCallTracingMiddleware;
import com.agentsquad.squad.v2.config.properties.ObservationProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
 * 初始化全局 OTel SDK 并导出到 Langfuse；导出故障不参与聊天事务或执行结果判定。
 */
@Configuration
@Slf4j
@EnableConfigurationProperties(ObservationProperties.class)
@ConditionalOnProperty(prefix = "agentsquad.v2.observation", name = "enabled", havingValue = "true")
public class ObservationConfiguration {
    @Resource
    private ObservationProperties properties;

    /**
     * 初始化有界异步 OTLP 导出队列，网络故障不占用 Agent 执行线程。
     * 显式开启观测时要求完整认证参数，避免运行中静默丢弃所有 Span。
     */
    @Bean(destroyMethod = "close")
    public OpenTelemetrySdk observationSdk() {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()
                || properties.getPublicKey() == null || properties.getPublicKey().isBlank()
                || properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new IllegalStateException("Langfuse 观测已开启，必须配置 endpoint、public-key 和 secret-key");
        }
        if (GlobalOpenTelemetry.isSet()) {
            throw new IllegalStateException("OpenTelemetry 全局实例已注册，避免覆盖现有 Agent 链路追踪");
        }

        var builder = OtlpHttpSpanExporter.builder()
                .setEndpoint(properties.getEndpoint())
                .setTimeout(Duration.ofSeconds(2));
        String credentials = properties.getPublicKey() + ":" + properties.getSecretKey();
        builder.addHeader("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        if (properties.getIngestionVersion() != null && !properties.getIngestionVersion().isBlank()) {
            builder.addHeader("x-langfuse-ingestion-version", properties.getIngestionVersion());
        }
        var exporter = builder.build();
        var processor = BatchSpanProcessor.builder(exporter)
                .setMaxQueueSize(2048)
                .setMaxExportBatchSize(256)
                .setScheduleDelay(Duration.ofSeconds(1))
                .build();
        var resource = io.opentelemetry.sdk.resources.Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), "agent-squad-v2");
        if (properties.getEnvironment() != null && !properties.getEnvironment().isBlank()) {
            resource.put(AttributeKey.stringKey("deployment.environment.name"), properties.getEnvironment());
        }
        if (properties.getServiceVersion() != null && !properties.getServiceVersion().isBlank()) {
            resource.put(AttributeKey.stringKey("service.version"), properties.getServiceVersion());
        }
        var provider = SdkTracerProvider.builder()
                .setResource(resource.build())
                .setSampler(Sampler.alwaysOn())
                .setSpanLimits(SpanLimits.builder()
                        .setMaxAttributeValueLength(properties.getMaxContentChars() * 2)
                        .build())
                .addSpanProcessor(processor).build();
        log.info("AgentScope 追踪已启用，OTLP 异步导出已配置，正文采集={}", properties.isCaptureContent());
        return OpenTelemetrySdk.builder().setTracerProvider(provider).buildAndRegisterGlobal();
    }

    /**
     * 补充模型 I/O，内容采集与脱敏策略独立于官方 OTel 生命周期。
     */
    @Bean
    public ModelIoTracingMiddleware modelIoTracingMiddleware() {
        return new ModelIoTracingMiddleware(properties.isCaptureContent(),
                properties.getMaxContentChars(), properties.getMaxToolContentChars());
    }

    @Bean
    public ToolCallTracingMiddleware toolCallTracingMiddleware() {
        return new ToolCallTracingMiddleware(properties.isCaptureContent(),
                properties.getMaxContentChars(), properties.getMaxToolContentChars());
    }

    @Bean
    public FrameworkTracingMiddleware frameworkTracingMiddleware() {
        return new FrameworkTracingMiddleware(properties.isCaptureContent(), properties.getMaxContentChars(),
                properties.getMaxToolContentChars());
    }

    @Bean
    public ReasoningTracingMiddleware reasoningTracingMiddleware(OpenTelemetrySdk sdk) {
        return new ReasoningTracingMiddleware(sdk.getTracer("com.agentsquad.squad.v2.agent"),
                properties.isCaptureContent(), properties.getMaxContentChars(), properties.getMaxToolContentChars());
    }

    /**
     * HTTP 追踪独立开关：与现有 Java Agent 自动埋点互斥，避免双层 HTTP Span。
     */
    @Bean
    @ConditionalOnProperty(prefix = "agentsquad.v2.observation", name = "http-enabled", havingValue = "true")
    public HttpTracingFilter httpTracingFilter() {
        return new HttpTracingFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentsquad.v2.observation", name = "http-enabled", havingValue = "true")
    public LangfuseTraceSerializer httpTraceSerializer() {
        return new LangfuseTraceSerializer(properties.isCaptureContent(), properties.getMaxContentChars(),
                properties.getMaxContentChars());
    }
}
