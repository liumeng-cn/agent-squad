package com.agentsquad.squad.v2.utils;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ToolType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 组件编码生成器
 * <p>
 * 统一按「全大写类型前缀-yyyyMMdd-3位当日递增序号」规则生成 MCP、工具和 Agent 的不可变业务编码，
 * 例如 AGT-M-20261124-001、AGT-S-20261124-001、TOOL-H-20261124-001、MCP-20261124-001。
 */
public final class ConfigCodeGenerator {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private ConfigCodeGenerator() {
    }

    /**
     * 生成当日 MCP 编码前缀（含末尾短横线，如 MCP-20261124-）
     */
    public static String mcpServerPrefix() {
        return dailyPrefix("MCP");
    }

    /**
     * 生成当日工具编码前缀（含末尾短横线，如 TOOL-H-20261124-）
     */
    public static String toolPrefix(ToolType type) {
        String prefix = switch (type) {
            case BUILTIN -> "TOOL-B";
            case HTTP -> "TOOL-H";
            case MCP -> "TOOL-M";
        };
        return dailyPrefix(prefix);
    }

    /**
     * 生成当日 Agent 编码前缀（含末尾短横线，如 AGT-M-20261124-）
     */
    public static String agentPrefix(AgentType type) {
        String prefix = type == AgentType.MASTER ? "AGT-M" : "AGT-S";
        return dailyPrefix(prefix);
    }

    /**
     * 根据当日已有最大编码计算下一个规整编码
     */
    public static String nextCode(String prefix, String latestCode, int retryOffset) {
        int currentSeq = parseSequence(prefix, latestCode);
        int nextSeq = currentSeq + 1 + retryOffset;
        return prefix + String.format("%03d", nextSeq);
    }

    private static String dailyPrefix(String typePrefix) {
        return typePrefix + "-" + LocalDate.now().format(DATE_FORMAT) + "-";
    }

    private static int parseSequence(String prefix, String latestCode) {
        if (latestCode == null || !latestCode.startsWith(prefix)) {
            return 0;
        }
        String suffix = latestCode.substring(prefix.length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
