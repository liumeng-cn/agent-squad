package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.agent.SquadAgentRegistry;
import com.agentsquad.squad.v2.enums.SquadExecutionStatus;
import com.agentsquad.squad.v2.event.SquadEventBuffer;
import io.agentscope.core.agent.AgentRun;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;

/** 单次消息执行上下文；所有可变执行状态均属于当前请求。 */
@Data
public class SquadExecutionBO {
    private Long executionId;
    private Long conversationId;
    private Long userId;
    private String runId;
    private RuntimeContext runtimeContext;
    private SquadEventBuffer eventBuffer;
    private SquadAgentRegistry agentRegistry;
    private Instant createdAt;
    private volatile Instant finishedAt;
    private AgentRun<AgentEvent> agentRun;
    private volatile SquadExecutionStatus status = SquadExecutionStatus.CREATED;
    private final AtomicLong sequence = new AtomicLong();
    /** 分配执行内单调递增序号。 */
    public long nextSequence() { return sequence.incrementAndGet(); }
    public synchronized void markRunning() { if (status == SquadExecutionStatus.CREATED) status = SquadExecutionStatus.RUNNING; }
    public synchronized boolean markCompleted() {
        if (status.isTerminal()) return false;
        status = SquadExecutionStatus.COMPLETED; finishedAt = Instant.now(); return true;
    }
    public synchronized boolean markFailed() {
        if (status.isTerminal()) return false;
        status = SquadExecutionStatus.FAILED; finishedAt = Instant.now(); return true;
    }
    public synchronized boolean markCancelled() {
        if (status.isTerminal()) return false;
        status = SquadExecutionStatus.CANCELLED; finishedAt = Instant.now(); return true;
    }
    public boolean isTerminal() { return status.isTerminal(); }
    public long getDurationMillis() {
        Instant end = finishedAt == null ? Instant.now() : finishedAt;
        return createdAt == null ? 0L : Duration.between(createdAt, end).toMillis();
    }
}