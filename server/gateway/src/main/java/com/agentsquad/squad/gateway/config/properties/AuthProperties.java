package com.agentsquad.squad.gateway.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 网关认证配置
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Data
@ConfigurationProperties("agentsquad.auth")
public class AuthProperties {
    /**
     * JWT私钥PEM内容
     */
    private String privateKey;
    /**
     * JWT公钥PEM内容
     */
    private String publicKey;
    /**
     * JWT签发方
     */
    private String issuer = "squad-gateway";
    /**
     * JWT接收方
     */
    private String audience = "squad-browser";
    /**
     * 固定登录有效期
     */
    private Duration lifetime = Duration.ofDays(7);
    /**
     * 当前测试验证码
     */
    private String verificationCode = "8888";
    /**
     * Cookie是否仅允许HTTPS
     */
    private boolean secureCookie;
    /**
     * 下游内部身份签名密钥
     */
    private String identityKey;
}
