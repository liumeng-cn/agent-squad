package com.agentsquad.experiments;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.InMemorySessionLogStore;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionExportSink;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLog;
import io.agentscope.core.session.SessionLogExporter;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 已提交事件导出与补偿实验
 * <p>
 * 验证固定 SDK 的确认序号、重复交付、上下文传递和末次失败恢复；仅使用隔离内存日志。
 */
class SdkExportContractTest {
    @Test
    void newProcessResumesFileJournalAtUnacknowledgedEvent(@TempDir Path directory) throws Exception {
        assertThat(runDiskProbe(directory, "fail")).contains("cursor=1,deliveries=[1, 2]");
        assertThat(runDiskProbe(directory, "resume")).contains("cursor=3,deliveries=[2, 3]");
    }

    @Test
    void lostAcknowledgementRedeliversWithoutSkipping() {
        SessionLog log = populatedLog();
        var sink = new RecordingExportSink();
        sink.failAtSequence.set(2);
        var exporter = new SessionLogExporter(log, sink);

        assertThatThrownBy(exporter::drain).isInstanceOf(IllegalStateException.class);
        assertThat(log.exportCursor(sink.name())).isEqualTo(1);
        assertThat(sink.accepted).hasSize(2);

        new SessionLogExporter(log, sink).drain();
        assertThat(log.exportCursor(sink.name())).isEqualTo(3);
        assertThat(sink.deliveries).containsExactly(1L, 2L, 2L, 3L);
        assertThat(sink.accepted).hasSize(3);
    }

    @Test
    void reopeningLogKeepsExportCursor() {
        var store = new InMemorySessionLogStore();
        var context = RuntimeContext.builder().userId("probe").sessionId("export").build();
        var key = new SessionKey("probe", "agent", "export");
        SessionLog log = store.open(key, context);
        commit(log);
        var sink = new RecordingExportSink();
        new SessionLogExporter(log, sink).drain();
        SessionLog reopened = store.open(key, context);
        new SessionLogExporter(reopened, sink).drain();
        assertThat(reopened.exportCursor(sink.name())).isEqualTo(3);
        assertThat(sink.deliveries).containsExactly(1L, 2L, 3L);
    }

    @Test
    void separateExportersCanDeliverSameEventConcurrently() throws Exception {
        var log = populatedLog();
        var sink = new RecordingExportSink();
        sink.firstEventBarrier = new CountDownLatch(2);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> new SessionLogExporter(log, sink).drain());
            var second = threads.submit(() -> new SessionLogExporter(log, sink).drain());
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(sink.deliveries.stream().filter(sequence -> sequence == 1).count()).isEqualTo(2);
        assertThat(sink.accepted).hasSize(3);
        assertThat(log.exportCursor(sink.name())).isEqualTo(3);
    }

    @Test
    void runtimeContextPreservesSinkAndTailCanBeRecovered(@TempDir Path directory) throws Exception {
        var sink = new RecordingExportSink();
        sink.rejectRunEnd = true;
        RuntimeContext original = RuntimeContext.builder().userId("probe-user").sessionId("tail-probe").build();
        original.put(SessionExportSink.CONTEXT_KEY, sink);
        Object copiedSink = RuntimeContext.builder(original).build().get(SessionExportSink.CONTEXT_KEY);
        assertThat(copiedSink).isSameAs(sink);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.text("stop")), false);
             var agent = SdkExecutionContractTest.agent(model, directory, new Toolkit(), 2)) {
            var session = agent.session(original);
            var receipt = session.submit("tail-command", "本地实验");
            assertThat(session.await(receipt).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            long head = session.log().head().seq();
            awaitTailAttempt(sink, head);
            assertThat(session.log().exportCursor(sink.name())).isLessThan(head);
            sink.rejectRunEnd = false;
            new SessionLogExporter(session.log(), sink).drain();
            assertThat(session.log().exportCursor(sink.name())).isEqualTo(head);
            assertThat(sink.accepted).hasSize((int) head);
            assertThat(model.calls()).isEqualTo(1);
        }
    }

    private void awaitTailAttempt(RecordingExportSink sink, long head) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!sink.deliveries.contains(head) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(sink.deliveries).contains(head);
    }

    private SessionLog populatedLog() {
        var context = RuntimeContext.builder().userId("probe").sessionId("export").build();
        SessionLog log = new InMemorySessionLogStore().open(new SessionKey("probe", "agent", "export"), context);
        commit(log);
        return log;
    }

    private void commit(SessionLog log) {
        var writer = log.acquire("writer", Duration.ofSeconds(10));
        List<SessionEvent> events = List.of(
                event(1), event(2), event(3));
        log.commit(writer, "batch", 0, events);
        log.release(writer);
    }

    private SessionEvent event(long sequence) {
        return new SessionEvent(1, "event-" + sequence, sequence, 1,
                "presentation/hint", "run", "turn", false, "{}");
    }

    private String runDiskProbe(Path directory, String mode) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), DiskJournalProbe.class.getName(),
                directory.toString(), mode).redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
