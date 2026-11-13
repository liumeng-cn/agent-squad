package com.agentsquad.squad.v2.exceptions;

/**
 * SSE 补收游标早于事件缓冲区最早保留位置时抛出。
 */
public class EventBufferExpiredException extends RuntimeException {
    private final long firstRetainedSequence;

    public EventBufferExpiredException(long firstRetainedSequence) {
        super("事件缓冲已滚动，最早可补收序号为 " + firstRetainedSequence);
        this.firstRetainedSequence = firstRetainedSequence;
    }

    public long getFirstRetainedSequence() {
        return firstRetainedSequence;
    }
}