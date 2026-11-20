package com.agentsquad.squad.v2.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 观测平台配置
 * <p>
 * 维护标准 OTLP HTTP 接收地址与 Langfuse 项目认证信息。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("agentsquad.v2.observation")
public class ObservationProperties {
    /**
     * 是否向 Langfuse 导出原生调用链。
     */
    private boolean enabled;

    /**
     * 是否采集用户和模型的公开正文；默认关闭，不采集完整 Prompt、推理或工具数据。
     */
    private boolean captureContent;

    /**
     * 是否启用独立 HTTP 过滤器，若已使用 OTel Java Agent 则不应同时开启。
     */
    private boolean httpEnabled;

    /**
     * 每条正文及属性的最大字符数，避免大上下文撑大 Span。
     */
    @Min(256)
    @Max(100000)
    private int maxContentChars = 4096;

    /**
     * 完整的 OTLP HTTP Trace 接收地址。
     */
    private String endpoint = "http://127.0.0.1:13000/api/public/otel/v1/traces";

    /**
     * Langfuse 项目公钥。
     */
    private String publicKey;

    /**
     * Langfuse 项目密钥。
     */
    private String secretKey;

    /**
     * 观测环境标识。
     */
    private String environment = "local";

    /**
     * 部署版本，留空时不写入资源属性。
     */
    private String serviceVersion;

    /**
     * Langfuse v4 直连的摄取版本；接入其他接收器时可留空。
     */
    private String ingestionVersion = "4";
}
