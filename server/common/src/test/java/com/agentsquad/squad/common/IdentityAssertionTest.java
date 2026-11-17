package com.agentsquad.squad.common;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 分布式身份可信边界测试
 * <p>
 * 验证请求路径、目标服务和签名不可替换。
 */
class IdentityAssertionTest {
    private final String key = "test-internal-key-32-characters-long";

    @Test
    void bindsSubjectAudienceAndRequest() {
        CurrentUserBO user = new CurrentUserBO();
        user.setUserId("100000");
        user.setSessionId("session");
        String token = IdentityAssertion.sign(user, "user-server", "GET", "/api/v2/users/me", key);
        assertEquals("100000", IdentityAssertion.verify(token, "user-server", "GET", "/api/v2/users/me", key).getUserId());
        assertThrows(IllegalArgumentException.class, () -> IdentityAssertion.verify(token, "v2-server", "GET", "/api/v2/users/me", key));
        assertThrows(IllegalArgumentException.class, () -> IdentityAssertion.verify(token, "user-server", "PATCH", "/api/v2/users/me", key));
        assertThrows(IllegalArgumentException.class, () -> IdentityAssertion.verify(token, "user-server", "GET", "/other", key));
        assertThrows(IllegalArgumentException.class, () -> IdentityAssertion.verify(token + "bad", "user-server", "GET", "/api/v2/users/me", key));
        assertThrows(IllegalArgumentException.class, () -> IdentityAssertion.verify(null, "user-server", "GET", "/api/v2/users/me", key));
    }
}
