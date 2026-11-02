package com.agentsquad.squad.identity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务服务校验网关身份断言的配置。
 *
 * <p>当前与上一把密钥在轮换窗口内都可验签；密钥只从环境注入，不写入配置仓库或日志。</p>
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
