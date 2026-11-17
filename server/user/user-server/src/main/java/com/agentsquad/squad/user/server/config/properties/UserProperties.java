package com.agentsquad.squad.user.server.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户服务配置
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Data
@ConfigurationProperties("agentsquad.user")
public class UserProperties {
    /**
     * 当前测试流程验证码
     */
    private String verificationCode = "8888";
    /**
     * 网关内部身份验签密钥
     */
    private String identityKey;
}
