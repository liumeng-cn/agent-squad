package com.agentsquad.squad.memory.domain;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

import java.time.LocalDateTime;

/**
 * 显式记忆的内容与生命周期规则。
 * <p>
 * 独立于Spring和存储，统一写入校验、有效期及删除墓碑的读取语义。
 */
public final class MemoryRules {
    private MemoryRules() {
    }

    public static void requireContent(String key, String content, LocalDateTime expiresAt, LocalDateTime now) {
        if (key == null || key.isBlank() || key.length() > 100 || key.chars().anyMatch(Character::isISOControl)
                || content == null || content.isBlank() || content.length() > 8000 || content.indexOf('\0') >= 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "记忆键须为1到100字且不含控制字符，内容须为1到8000字");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "记忆有效期必须晚于当前时间");
        }
    }

    public static boolean active(MemoryRow memory, LocalDateTime now) {
        return "ACTIVE".equals(memory.status()) && (memory.expiresAt() == null || memory.expiresAt().isAfter(now));
    }

    public static void requireVersion(MemoryRow memory, long expected) {
        if (expected < 0 || memory.version() != expected || !"ACTIVE".equals(memory.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "记忆版本或状态已变化，请刷新后操作");
        }
    }
}
