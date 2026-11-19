package com.agentsquad.squad.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 网关装配验证
 *
 * 加载真实客户端与认证过滤器，验证禁止循环依赖时上下文能够启动。
 */
@SpringBootTest(properties = {
        "agentsquad.auth.identity-key=gateway-context-test-key",
        "spring.main.allow-circular-references=false"
})
class GatewayApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
