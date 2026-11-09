package com.agentsquad.squad.agent.session;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionInbox;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLog;
import io.agentscope.core.session.SessionLogStore;
import io.agentscope.harness.agent.session.AgentSession;

import java.util.List;
import java.util.Objects;
import com.agentsquad.squad.enums.NativeTaskState;

/**
 * 原生会话已提交事实读取器
 * <p>
 * 使用调用方实际日志后端读取任意固定Agent的会话，不构建Worker、不接触业务guard，也不启动或恢复执行。
 */
public final class CommittedSessionReader {
    private static final int MAX_READ_BATCH = 1000;
    private final SessionLogStore store;

    public CommittedSessionReader(SessionLogStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public List<SessionEvent> read(String userId, String agentId, String sessionId, long after, int limit) {
        if (after < 0 || limit < 1 || limit > MAX_READ_BATCH) {
            throw new IllegalArgumentException("原生日志读取范围无效");
        }
        return open(userId, agentId, sessionId).readAfter(after, limit);
    }

    /**
     * 按原提交找到所属Turn，再选择该Turn最新执行命令；只识别已提交状态，不伪造进程内starting状态。
     * writer失效时返回interrupted，由独立恢复服务决定是否继续；读取不会自行领取writer。
     */
    public AgentSession.Task inspect(String userId, String agentId, String sessionId, String commandId) {
        SessionLog log = open(userId, agentId, sessionId);
        var head = log.head();
        var inbox = new SessionInbox(log).snapshot();
        var original = inbox.commands().stream()
                .filter(command -> command.execution() && command.id().equals(commandId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("原生提交不存在"));
        var latest = original;
        for (var command : inbox.commands()) {
            if (command.execution() && original.turnId().equals(command.turnId())) {
                latest = command;
            }
        }
        if (inbox.rejected().containsKey(latest.id())) {
            return new AgentSession.Task(latest.id(), latest.turnId(), null,
                    NativeTaskState.REJECTED.nativeValue(), inbox.rejected().get(latest.id()));
        }

        String runId = null;
        NativeTaskState status = NativeTaskState.QUEUED;
        for (var event : log.scan(0, head.seq())) {
            if (event.type().equals("inbox/started") && latest.id().equals(event.data().get("commandId"))) {
                runId = event.executionRunId();
                status = NativeTaskState.RUNNING;
            }
            if (runId != null && runId.equals(event.executionRunId()) && event.type().equals("run/end")) {
                status = NativeTaskState.from(event.data().get("status"));
            }
        }
        if (status == NativeTaskState.RUNNING && (!Objects.equals(runId, head.owner())
                || head.leaseUntil() <= System.currentTimeMillis())) {
            status = NativeTaskState.INTERRUPTED;
        }
        return new AgentSession.Task(latest.id(), latest.turnId(), runId, status.nativeValue(), null);
    }

    /** 日志写入者已释放或失效才可认定静止；发出 interrupt 本身不等于运行已经停止。 */
    public boolean quiescent(String userId, String agentId, String sessionId) {
        var log = open(userId, agentId, sessionId);
        var head = log.head();
        var inbox = new SessionInbox(log).snapshot();
        return inbox.runId() == null
                && (head.owner() == null || head.leaseUntil() <= System.currentTimeMillis());
    }

    private SessionLog open(String userId, String agentId, String sessionId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("必须指定日志归属用户");
        }
        var context = RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
        return store.open(new SessionKey(userId, agentId, sessionId), context);
    }
}
