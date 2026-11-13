package com.agentsquad.squad.v2.event;

import com.agentsquad.squad.common.model.SquadMsg;
import com.agentsquad.squad.v2.exceptions.EventBufferExpiredException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/** 单次执行的有界事件缓冲；断线订阅者不会取消 AgentRun。 */
public class SquadEventBuffer {
    private final int capacity;
    private final Sinks.Many<SquadMsg> sink;
    private long publishedCount;
    private long firstRetainedSequence = 1L;
    private boolean completed;
    public SquadEventBuffer(int capacity) {
        this.capacity = Math.max(100, capacity);
        this.sink = Sinks.many().replay().limit(this.capacity);
    }
    /** 将事件写入缓冲并广播。 */
    public synchronized void publish(SquadMsg event) {
        if (completed) throw new IllegalStateException("执行事件缓冲已经结束");
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result != Sinks.EmitResult.OK) throw new IllegalStateException("写入事件缓冲失败：" + result);
        publishedCount++;
        if (publishedCount > capacity) firstRetainedSequence = Math.max(1L, event.getSequence() - capacity + 1L);
    }
    /** 补收游标之后的事件；游标过期时明确失败。 */
    public synchronized Flux<SquadMsg> subscribeAfter(Long afterSequence) {
        long cursor = afterSequence == null ? 0L : afterSequence;
        if (cursor < firstRetainedSequence - 1L) throw new EventBufferExpiredException(firstRetainedSequence);
        return sink.asFlux().filter(event -> event.getSequence() != null && event.getSequence() > cursor);
    }
    /** 结束事件流，保留已缓存事件供后续补收。 */
    public synchronized void complete() {
        if (!completed) { completed = true; sink.tryEmitComplete(); }
    }
}