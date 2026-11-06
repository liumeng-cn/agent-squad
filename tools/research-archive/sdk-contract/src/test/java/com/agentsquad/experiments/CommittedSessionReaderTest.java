package com.agentsquad.experiments;

import com.agentsquad.squad.integration.agentscope.journal.CommittedSessionReader;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.session.InMemorySessionLogStore;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionInbox;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLog;
import io.agentscope.core.session.SessionLogStore;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生产纯读适配器验证
 * <p>
 * 对照真实SDK的提交收据，并从Master的workspace读取另一Agent日志，证明无需构建或启动Worker。
 */
class CommittedSessionReaderTest {
    @Test
    void receiptMatchesSdkAfterSuspendResumeAndAnotherTurn(@TempDir Path directory) throws Exception {
        var store = new InMemorySessionLogStore();
        var toolkit = new Toolkit();
        toolkit.registerAgentTool(new ControlledFinishTool(ControlledFinishMode.SUSPEND));
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(false), ScriptedModelServer.text("stop")), false);
             var agent = SdkExecutionContractTest.agent(model, directory, toolkit, 4, false, store)) {
            var context = RuntimeContext.builder().userId("user").sessionId("session").build();
            var session = agent.session(context);
            var submitted = session.submit("initial", "挂起恢复读取实验");
            var suspended = session.await(submitted).block(Duration.ofSeconds(15));
            var reader = new CommittedSessionReader(store);
            assertThat(reader.inspect("user", "probe-agent", "session", "initial")).isEqualTo(suspended);
            var answer = session.respond(session.pending().keySet().iterator().next(), "已核实的外部结果");
            var completed = session.await(answer).block(Duration.ofSeconds(15));
            assertThat(reader.inspect("user", "probe-agent", "session", "initial")).isEqualTo(completed);
            var next = session.submit("next", "另一轮实验");
            assertThat(session.await(next).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            assertThat(reader.inspect("user", "probe-agent", "session", "initial")).isEqualTo(completed);
            assertThat(model.calls()).isEqualTo(2);
        }
    }

    @Test
    void queuedReceiptDoesNotAcquireWriterOrConsumeInput() {
        var store = new InMemorySessionLogStore();
        var log = log(store);
        accept(log, "submit", "submit");
        long inboxHead = log.inbox().head().seq();
        var reader = new CommittedSessionReader(store);
        assertThat(reader.inspect("user", "worker", "session", "submit").status()).isEqualTo("queued");
        assertThat(reader.read("user", "worker", "session", 0, 10)).isEmpty();
        assertThat(log.head().seq()).isZero();
        assertThat(log.head().owner()).isNull();
        assertThat(log.inbox().head().seq()).isEqualTo(inboxHead);
        assertThatThrownBy(() -> reader.inspect("user", "worker", "session", "absent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lostWriterIsReportedWithoutStartingRecovery() {
        var store = new InMemorySessionLogStore();
        var log = log(store);
        accept(log, "submit", "submit");
        var writer = log.acquire("run", Duration.ofSeconds(10));
        log.commit(writer, "start", 0, List.of(event(1, "inbox/started", "run", Map.of("commandId", "submit"))));
        var reader = new CommittedSessionReader(store);
        assertThat(reader.inspect("user", "worker", "session", "submit").status()).isEqualTo("running");
        log.release(writer);
        assertThat(reader.inspect("user", "worker", "session", "submit").status()).isEqualTo("interrupted");
        assertThat(log.head().owner()).isNull();
        assertThat(log.head().seq()).isEqualTo(1);
    }

    @Test
    void latestResumeDoesNotInheritEarlierRunStatus() {
        var store = new InMemorySessionLogStore();
        var log = log(store);
        accept(log, "submit", "submit");
        commitRun(log, "submit", "first-run", "suspended");
        var reader = new CommittedSessionReader(store);
        assertThat(reader.inspect("user", "worker", "session", "submit").status()).isEqualTo("suspended");
        accept(log, "resume", "resume");
        var queued = reader.inspect("user", "worker", "session", "submit");
        assertThat(queued.commandId()).isEqualTo("resume");
        assertThat(queued.status()).isEqualTo("queued");
        assertThat(queued.runId()).isNull();
        commitRun(log, "resume", "second-run", "completed");
        var completed = reader.inspect("user", "worker", "session", "submit");
        assertThat(completed.status()).isEqualTo("completed");
        assertThat(completed.runId()).isEqualTo("second-run");
    }

    @Test
    void unknownNativeOutcomeDoesNotBecomeSuccessful() {
        var store = new InMemorySessionLogStore();
        var log = log(store);
        accept(log, "submit", "submit");
        commitRun(log, "submit", "run", "future-status");
        assertThat(new CommittedSessionReader(store).inspect("user", "worker", "session", "submit").status())
                .isEqualTo("unknown");
    }

    @Test
    void readsOtherAgentFromExistingWorkspaceWithoutWorkerConstruction(@TempDir Path directory) throws Exception {
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.text("stop")), false);
             var worker = workspaceAgent(model, directory, "worker");
             var master = workspaceAgent(model, directory, "master")) {
            var context = RuntimeContext.builder().userId("user").sessionId("session").build();
            var session = worker.session(context);
            var submitted = session.submit("real-submit", "纯读边界实验");
            var expected = session.await(submitted).block(Duration.ofSeconds(15));
            var reader = new CommittedSessionReader(new WorkspaceSessionLogStore(master.getWorkspaceManager()));
            var observed = reader.inspect("user", "worker", "session", "real-submit");
            assertThat(observed).isEqualTo(expected);
            assertThat(reader.read("user", "worker", "session", 0, 1000).stream().map(SessionEvent::type).toList())
                    .contains("turn/output");
            assertThat(reader.read("other-user", "worker", "session", 0, 1000)).isEmpty();
            assertThat(model.calls()).isEqualTo(1);
            assertThat(session.log().head().owner()).isNull();
        }
    }

    private SessionLog log(SessionLogStore store) {
        return store.open(new SessionKey("user", "worker", "session"),
                RuntimeContext.builder().userId("user").sessionId("session").build());
    }

    private void accept(SessionLog log, String commandId, String kind) {
        new SessionInbox(log).locked(tx -> tx.accept(new SessionInbox.Command(commandId, kind,
                "turn", List.of(new UserMessage("受控实验输入")), List.of())));
    }

    private void commitRun(SessionLog log, String commandId, String runId, String status) {
        long previous = log.head().seq();
        var writer = log.acquire(runId, Duration.ofSeconds(10));
        log.commit(writer, "batch-" + commandId, previous, List.of(
                event(previous + 1, "inbox/started", runId, Map.of("commandId", commandId)),
                event(previous + 2, "run/end", runId, Map.of("status", status))));
        log.release(writer);
    }

    private SessionEvent event(long sequence, String type, String runId, Map<String, Object> payload) {
        return new SessionEvent(1, "event-" + sequence, sequence, 1, type, runId, "turn", false,
                JsonUtils.getJsonCodec().toJson(payload));
    }

    private HarnessAgent workspaceAgent(ScriptedModelServer model, Path directory, String agentId) {
        return HarnessAgent.builder().name("纯读实验").agentId(agentId).workspace(directory)
                .model(OpenAIChatModel.builder().apiKey("local-probe-only").baseUrl(model.baseUrl()).modelName("probe").stream(false).build())
                .filesystem(new RemoteFilesystemSpec(new InMemoryStore()).isolationScope(IsolationScope.USER))
                .toolkit(new Toolkit()).disableSubagents().disableFilesystemTools().disableShellTool().disableWebTools()
                .disableMemoryTools().disableMemoryHooks().disableDefaultWorkspaceSkills().disableWorkspaceContext()
                .disableToolsConfig().disableCompaction().build();
    }
}
