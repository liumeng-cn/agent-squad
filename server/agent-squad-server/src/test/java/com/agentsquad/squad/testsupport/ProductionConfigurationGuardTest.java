package com.agentsquad.squad.testsupport;

import com.agentsquad.squad.config.ProductionConfigurationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 生产配置的纯内存校验。
 * <p>
 * 验证DDL和身份安全开关，不创建Spring上下文或访问数据库。
 */
class ProductionConfigurationGuardTest {
    @Test
    void rejectsUnsafeSettingsAndAcceptsSafeConfiguration() {
        ProductionConfigurationGuard guard = new ProductionConfigurationGuard();
        ReflectionTestUtils.setField(guard, "sqlInitMode", "always");
        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
        ReflectionTestUtils.setField(guard, "sqlInitMode", "never");
        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
        ReflectionTestUtils.setField(guard, "strictIdentity", true);
        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
        ReflectionTestUtils.setField(guard, "enforceUserStatus", true);
        assertDoesNotThrow(guard::afterPropertiesSet);
        ReflectionTestUtils.setField(guard, "allowUnsignedLocal", true);
        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }
}
