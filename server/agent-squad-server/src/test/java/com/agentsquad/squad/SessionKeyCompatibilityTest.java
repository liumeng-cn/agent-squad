package com.agentsquad.squad;

import io.agentscope.core.session.SessionKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定版本 AgentScope 核心标识兼容性测试。
 *
 * <p>该测试不访问模型、数据库或外部系统，只验证依赖树和基础标识编码边界。</p>
 */
class SessionKeyCompatibilityTest {

    @Test
    void shouldBuildSessionKeyWithStableIdentifiers() {
        String userId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String agentId = "0123456789abcdef0123456789abcdef";

        SessionKey key = new SessionKey(agentId, userId, sessionId);

        assertThat(key).isNotNull();
        assertThat(key.toString()).isNotBlank();
        assertThat(key.toString().length()).isLessThanOrEqualTo(255);
    }
}
