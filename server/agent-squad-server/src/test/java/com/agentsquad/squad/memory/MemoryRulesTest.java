package com.agentsquad.squad.memory;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.memory.domain.MemoryRow;
import com.agentsquad.squad.memory.domain.MemoryRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 显式记忆业务规则的独立验证。
 * <p>
 * 不启动Spring或数据库，覆盖内容边界、有效期和版本屏障。
 */
class MemoryRulesTest {
    @Test
    void rejectsControlKeysAndExpiredWrites() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 0, 0);
        assertThatThrownBy(() -> MemoryRules.requireContent("bad\nkey", "有效内容", null, now))
                .isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> MemoryRules.requireContent("key", "有效内容", now, now))
                .isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> MemoryRules.requireContent("key", "\u0000", null, now))
                .isInstanceOf(RuntimeBusinessException.class);
        MemoryRules.requireContent("回复语言", "中文", now.plusDays(1), now);
    }

    @Test
    void deletedExpiredAndChangedVersionCannotBeUsed() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 0, 0);
        MemoryRow active = row("ACTIVE", 1, now.plusDays(1));
        assertThat(MemoryRules.active(active, now)).isTrue();
        assertThat(MemoryRules.active(row("DELETED", 1, null), now)).isFalse();
        assertThat(MemoryRules.active(row("ACTIVE", 1, now), now)).isFalse();
        assertThatThrownBy(() -> MemoryRules.requireVersion(active, 0)).isInstanceOf(RuntimeBusinessException.class);
        assertThatThrownBy(() -> MemoryRules.requireVersion(row("DELETED", 1, null), 1)).isInstanceOf(RuntimeBusinessException.class);
    }

    private MemoryRow row(String status, long version, LocalDateTime expires) {
        return new MemoryRow("memory", "owner", "key", "value", status, version, expires, null, null, 1);
    }
}
