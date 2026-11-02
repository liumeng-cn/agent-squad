package com.agentsquad.squad.gateway.assertion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关内部身份断言配置。
 *
 * <p>密钥只从环境注入，禁止写入 Git 或日志；当前密钥用于签名，上一把密钥仅在业务端接受
 * 轮换窗口内的旧断言。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agentsquad.assertion")
public class AssertionProperties {

    private String issuer = "agent-squad-gateway";
    private String audience = "agent-squad-server";
    private long ttlSeconds = 30;
    private long clockSkewSeconds = 5;
    private String currentKid = "k1";
    private String currentSecret = "";
    private String previousKid = "";
    private String previousSecret = "";
}
