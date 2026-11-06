package com.agentsquad.experiments;

import com.agentsquad.squad.integration.agentscope.journal.RuntimeJournalStore;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionLogExporter;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 数据库日志与收件的独立进程实验
 * <p>
 * 第一个进程在收件提交后立即halt，第二个通过官方SDK重新读取日志并补齐导出；不执行真实模型。
 */
public final class MySqlJournalProbe {
    private MySqlJournalProbe() {
    }

    public static void main(String[] args) throws Exception {
        var sink = new MySqlProbeStore(args[1].equals("crash"));
        sink.initialize();
        var dialect = AbstractJdbcDialect.from(sink.source()).autoCreateTable(true).build();
        var distributed = JdbcDistributedStore.create(sink.source(), dialect);
        try (var server = new ScriptedModelServer(List.of(ScriptedModelServer.text("stop")), false);
             var agent = HarnessAgent.builder().name("数据库日志实验").agentId("mysql-probe")
                     .workspace(Path.of(args[0]))
                     .model(OpenAIChatModel.builder().apiKey("local-probe-only").baseUrl(server.baseUrl()).modelName("probe").build())
                     .toolkit(new Toolkit()).distributedStore(distributed)
                     .sessionLogStore(new RuntimeJournalStore(distributed.baseStore()))
                     .filesystem(new RemoteFilesystemSpec())
                     .disableSubagents().disableFilesystemTools().disableShellTool().disableWebTools()
                     .disableMemoryTools().disableMemoryHooks().disableDefaultWorkspaceSkills()
                     .disableWorkspaceContext().disableToolsConfig().build()) {
            var context = RuntimeContext.builder().userId("mysql-user").sessionId("mysql-session").build();
            var log = agent.session(context).log();
            if (args[1].equals("crash")) {
                var writer = log.acquire("mysql-writer", Duration.ofSeconds(10));
                log.commit(writer, "mysql-batch", 0, List.of(event(1), event(2), event(3)));
                log.release(writer);
            }
            new SessionLogExporter(log, sink).drain();
            sink.consumeTwice();
            System.out.println("cursor=" + log.exportCursor(sink.name()) + ",inbox=" + sink.count("probe_inbox")
                    + ",effects=" + sink.count("probe_effect") + ",models=" + server.calls());
        }
    }

    private static SessionEvent event(long sequence) {
        return new SessionEvent(1, "mysql-event-" + sequence, sequence, 1,
                "presentation/hint", "run", "turn", false, "{}");
    }
}
