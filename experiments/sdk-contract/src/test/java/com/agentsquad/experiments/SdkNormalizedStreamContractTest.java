package com.agentsquad.experiments;

import com.agentsquad.squad.integration.agentscope.completion.ModelCompletionNormalizer;
import com.agentsquad.squad.integration.agentscope.completion.ModelEvidenceIssue;
import com.agentsquad.squad.integration.agentscope.completion.ModelFinishReason;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实流式日志到规范化结果的准入验证
 * <p>
 * 直接对固定 SDK 已提交的日志应用业务算法，原生停止原因丢失仍保留，适配结果需独立正确。
 */
class SdkNormalizedStreamContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"stop", "length", "content_filter", "tool_calls", "new_reason"})
    @NullSource
    void normalizesCommittedStreamWithoutChangingSdk(String reason, @TempDir Path directory) throws Exception {
        // tool_calls 仅验证停止原因映射，不声明模型实际发出了可执行工具。
        String finish = reason == null ? "null" : "\"" + reason + "\"";
        String stream = "data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"probe\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"测试正文\"},\"finish_reason\":" + finish + "}]}\n\n"
                + "data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"probe\",\"choices\":[],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":4,\"total_tokens\":11}}\n\n"
                + "data: [DONE]\n\n";
        try (var server = new ScriptedModelServer(List.of(stream), true);
             var agent = SdkExecutionContractTest.agent(server, directory, new Toolkit(), 2, true)) {
            var session = SdkExecutionContractTest.session(agent);
            var receipt = session.submit("normalize-tail", "本地适配验证");
            var settled = session.await(receipt).block(Duration.ofSeconds(15));
            assertThat(settled).isNotNull();
            var events = session.log().readAfter(0, 1000);
            var end = events.stream().filter(event -> event.type().equals("model/end")).findFirst().orElseThrow();
            assertThat(end.data().get("finishReason")).isNull();
            String call = (String) end.data().get("modelCallId");
            var result = new ModelCompletionNormalizer().normalize(end.executionRunId(), end.turnId(), call, events);
            assertThat(result.hasConfirmedNormalStop()).isEqualTo("stop".equals(reason));
            assertThat(result.hasTrustedUsage()).isTrue();
            assertThat(result.usage().inputTokens()).isEqualTo(7);
            assertThat(result.usage().outputTokens()).isEqualTo(4);
            assertThat(result.usageSequence()).isEqualTo(end.seq());
            if (reason == null) {
                assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.MISSING_REASON);
            } else if (reason.equals("new_reason")) {
                assertThat(result.finishReason()).isEqualTo(ModelFinishReason.UNKNOWN);
                assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.UNKNOWN_REASON);
            } else {
                assertThat(result.completionIssue()).isEqualTo(ModelEvidenceIssue.NONE);
                assertThat(result.reasonSequence()).isLessThan(result.endSequence());
            }
            assertThat(server.calls()).isEqualTo(1);
        }
    }
}
