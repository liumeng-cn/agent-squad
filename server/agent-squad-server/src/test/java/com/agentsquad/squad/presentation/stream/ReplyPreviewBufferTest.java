package com.agentsquad.squad.presentation.stream;

import com.agentsquad.squad.bo.ReplyPreviewTargetBO;
import com.agentsquad.squad.service.ReplyPreviewBuffer;
import com.agentsquad.squad.enums.ReplyPreviewPhase;
import com.agentsquad.squad.event.ReplyPreviewSignal;
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
    void abortRetiresOnlyThisStreamAndNeverPublishesLateText() {
        var signals = new ArrayList<ReplyPreviewSignal>();
        var buffer = new ReplyPreviewBuffer(new ReplyPreviewTargetBO("u", "c", "e", "m"), "run", signals::add);
        buffer.append("不完整正文");
        buffer.abort();
        var aborted = signals.getLast().preview();
        assertThat(aborted.phase()).isEqualTo(ReplyPreviewPhase.ABORTED);
        assertThat(aborted.text()).isEmpty();
        int count = signals.size();
        buffer.append("迟到正文");
        buffer.thinking();
        buffer.flush();
        buffer.abort();
        assertThat(signals).hasSize(count);
    }

    @Test
    void preservesSurrogatePairAcrossChunksAndReplacesWholeText() {
        var signals = new ArrayList<ReplyPreviewSignal>();
        var buffer = new ReplyPreviewBuffer(new ReplyPreviewTargetBO("u", "c", "e", "m"), "run", signals::add);
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
        var buffer = new ReplyPreviewBuffer(new ReplyPreviewTargetBO("u", "c", "e", "m"), "run", signals::add);
        buffer.append("a".repeat(65535) + "😀");
        buffer.flush();
        assertThat(signals.getLast().preview().text()).hasSize(65535);
        assertThat(Character.isHighSurrogate(signals.getLast().preview().text().charAt(65534))).isFalse();
    }

    @Test
    void reportsThinkingPhaseWithoutReasoningTextAndMergesRepeats() {
        var signals = new ArrayList<ReplyPreviewSignal>();
        var buffer = new ReplyPreviewBuffer(new ReplyPreviewTargetBO("u", "c", "e", "m"), "run", signals::add);
        buffer.thinking();
        buffer.thinking();
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().preview().phase()).isEqualTo(ReplyPreviewPhase.THINKING);
        assertThat(signals.getFirst().preview().text()).isEmpty();
        buffer.append("正文");
        buffer.flush();
        assertThat(signals.getLast().preview().phase()).isEqualTo(ReplyPreviewPhase.ANSWERING);
        assertThat(signals.getLast().preview().text()).isEqualTo("正文");
    }
}
