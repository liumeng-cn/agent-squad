package com.agentsquad.squad.v2.utils;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import java.util.UUID;

/**
 * V2 标识生成工具。
 */
public final class SquadIdUtils {
    private SquadIdUtils() {
    }

    /**
     * 生成分布式 Snowflake Long ID。
     */
    public static Long nextSnowflakeId() {
        return IdWorker.getId();
    }

    /**
     * 生成不包含横线的 32 位 UUID。
     */
    public static String uuidWithoutHyphens() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析正数 Snowflake ID。
     */
    public static Long parsePositiveLong(String value) {
        if (value == null || value.isBlank()) return null;
        long parsed = Long.parseLong(value);
        if (parsed <= 0L) throw new IllegalArgumentException("ID 必须为正数");
        return parsed;
    }
}