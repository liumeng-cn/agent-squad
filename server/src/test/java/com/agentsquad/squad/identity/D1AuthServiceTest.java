package com.agentsquad.squad.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D1 离线认证测试，覆盖登录、校验和注销生命周期。 */
class D1AuthServiceTest {

    @Test
    void shouldIssueAndInvalidateSession() {
        D1AuthService service = new D1AuthService();

        String token = service.login("demo", "demo");
        assertThat(service.authenticate(token).userId()).isEqualTo("d1-demo-user");

        service.logout(token);
        assertThatThrownBy(() -> service.authenticate(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("登录已失效");
    }
}
