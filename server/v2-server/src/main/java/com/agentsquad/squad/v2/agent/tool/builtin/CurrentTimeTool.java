package com.agentsquad.squad.v2.agent.tool.builtin;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 当前时间工具
 * <p>
 * 内置工具示例：按指定时区返回当前时间，不访问外部系统、不修改数据。
 */
public class CurrentTimeTool {
    public static final String NAME = "current_time";

    /**
     * 查询当前时间
     * <p>
     * 未传时区时使用 Asia/Shanghai；时区无效时抛出参数错误，由框架转换为工具错误结果。
     */
    @Tool(name = NAME, description = "查询当前日期和时间。需要知道今天日期、当前时间或进行时间换算时使用。", readOnly = true)
    public String now(
            @ToolParam(name = "timezone", description = "IANA 时区，例如 Asia/Shanghai；不传时使用 Asia/Shanghai", required = false) String timezone) {
        String zoneText = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
        ZoneId zone;
        try {
            zone = ZoneId.of(zoneText);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("时区无效：" + zoneText);
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        return now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "（" + zone.getId() + "）";
    }
}
