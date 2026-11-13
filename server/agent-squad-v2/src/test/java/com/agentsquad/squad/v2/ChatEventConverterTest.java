package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.converter.ChatEventConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agui.event.AguiEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公开事件边界验证
 * <p>
 * 验证内部载荷不会随 SDK 事件进入公开输出，且事件时间不被替换。
 */
class ChatEventConverterTest {
    @Test
    void exposesOnlyDeclaredFields() throws Exception {
        var converter = new ChatEventConverter();
        var source = new AguiEvent.ToolCallResult("s", "r", "tool", "private-result", "tool", "m", 123L, "private-raw");
        var output = converter.convert(source).orElseThrow();
        assertThat(output.getTimestamp()).isEqualTo(123L);
        assertThat(output.getToolCallId()).isEqualTo("tool");
        assertThat(new ObjectMapper().writeValueAsString(output))
                .doesNotContain("private-result", "private-raw", "rawEvent", "content");
        assertThat(converter.convert(new AguiEvent.Custom("s", "r", "unknown", "private"))).isEmpty();
    }
}
