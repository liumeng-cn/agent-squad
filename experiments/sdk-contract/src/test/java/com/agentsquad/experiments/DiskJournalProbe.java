package com.agentsquad.experiments;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLogExporter;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 独立进程文件日志探针
 * <p>
 * 两个 JVM 分别模拟确认丢失和重新导出，只输出游标及事件序号，不连接模型或业务数据库。
 */
public final class DiskJournalProbe {
    private DiskJournalProbe() {
    }

    public static void main(String[] args) {
        var filesystem = new LocalFilesystem(Path.of(args[0]));
        var store = new WorkspaceSessionLogStore(filesystem);
        var context = RuntimeContext.builder().userId("probe").sessionId("disk").build();
        var log = store.open(new SessionKey("probe", "agent", "disk"), context);
        var sink = new RecordingExportSink();
        if (args[1].equals("fail")) {
            var writer = log.acquire("first-process", Duration.ofSeconds(10));
            log.commit(writer, "disk-batch", 0, List.of(event(1), event(2), event(3)));
            log.release(writer);
            sink.failAtSequence.set(2);
            try {
                new SessionLogExporter(log, sink).drain();
                throw new AssertionError("确认丢失未触发");
            } catch (IllegalStateException expected) {
                if (log.exportCursor(sink.name()) != 1) {
                    throw new AssertionError("错误的失败游标");
                }
            }
        } else {
            new SessionLogExporter(log, sink).drain();
        }
        System.out.println("cursor=" + log.exportCursor(sink.name()) + ",deliveries=" + sink.deliveries);
    }

    private static SessionEvent event(long sequence) {
        return new SessionEvent(1, "disk-event-" + sequence, sequence, 1,
                "presentation/hint", "run", "turn", false, "{}");
    }
}
