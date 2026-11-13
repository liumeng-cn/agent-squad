package com.agentsquad.squad.v2.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 观测平台配置
 * <p>
 * 维护标准 OTLP HTTP 接收地址与 Langfuse 项目认证信息。
 */
@Getter
@Setter
@ConfigurationProperties("agentsquad.v2.observation")
public class ObservationProperties {
    /**
     * 是否向 Studio 导出原生调用链。
     */
    private boolean enabled;

    /**
     * 完整的 OTLP HTTP Trace 接收地址。
     */
    private String endpoint = "http://127.0.0.1:13001/v1/traces";

    /** Langfuse 项目公钥，Studio 模式为空。 */
    private String publicKey;

    /** Langfuse 项目密钥，Studio 模式为空。 */
    private String secretKey;

    /**
     * 观测环境标识。
     */
    private String environment = "local";
}
