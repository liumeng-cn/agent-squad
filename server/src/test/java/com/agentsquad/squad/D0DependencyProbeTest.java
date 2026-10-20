package com.agentsquad.squad;

import io.agentscope.core.session.SessionKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D0 依赖探针：验证固定版本 AgentScope 的核心标识 API 可以被应用编译和调用。
 *
 * <p>该测试不访问模型、数据库或外部系统，只验证依赖树和基础标识编码边界。</p>
 */
class D0DependencyProbeTest {

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
