package com.agentsquad.squad.presentation.stream;

import com.agentsquad.squad.conversation.domain.ReplyPreviewTarget;
import com.agentsquad.squad.integration.agentscope.ReplyPreviewBuffer;
import com.agentsquad.squad.presentation.service.ReplyPreviewSignal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 临时正文边界验证
 * <p>
 * 核对UTF-16代理对、合并与容量，不把临时文本测试当作SDK渠道隔离或真实网络验证。
 */
class ReplyPreviewBufferTest {
    @Test
    void preservesSurrogatePairAcrossChunksAndReplacesWholeText() {
        var signals = new ArrayList<ReplyPreviewSignal>();
        var buffer = new ReplyPreviewBuffer(new ReplyPreviewTarget("u", "c", "e", "m"), "run", signals::add);
        buffer.append("首段\uD83D");
        assertThat(signals.getFirst().preview().text()).isEqualTo("首段");
        buffer.append("\uDE00结尾");
        buffer.flush();
        assertThat(signals.getLast().preview().text()).isEqualTo("首段😀结尾");
        assertThat(signals.getLast().preview().revision()).isEqualTo(2);
        assertThat(signals.getLast().preview().offset()).isZero();
        assertThat(signals.getLast().preview().replace()).isTrue();
        buffer.flush();
        assertThat(signals).hasSize(2);
    }

    @Test
    void boundsTextWithoutPublishingHalfSurrogatePair() {
        var signals = new ArrayList<ReplyPreviewSignal>();
        var buffer = new ReplyPreviewBuffer(new ReplyPreviewTarget("u", "c", "e", "m"), "run", signals::add);
        buffer.append("a".repeat(65535) + "😀");
        buffer.flush();
        assertThat(signals.getLast().preview().text()).hasSize(65535);
        assertThat(Character.isHighSurrogate(signals.getLast().preview().text().charAt(65534))).isFalse();
    }
}
