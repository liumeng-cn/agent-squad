package com.agentsquad.experiments;

import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionExportSink;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可注入确认失败的事件接收器
 * <p>
 * 模拟已接收但确认丢失的窗口，唯一事件集合仅验证内存幂等，不代表数据库持久性。
 */
final class RecordingExportSink implements SessionExportSink {
    final Set<String> accepted = ConcurrentHashMap.newKeySet();
    final List<Long> deliveries = new CopyOnWriteArrayList<>();
    final AtomicLong failAtSequence = new AtomicLong(-1);
    volatile boolean rejectRunEnd;
    volatile CountDownLatch firstEventBarrier;

    @Override
    public String name() {
        return "squad-contract-probe";
    }

    @Override
    public void accept(SessionEvent event) {
        deliveries.add(event.seq());
        if (event.seq() == 1 && firstEventBarrier != null) {
            firstEventBarrier.countDown();
            try {
                if (!firstEventBarrier.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发导出未同时进入");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("并发实验被中断", exception);
            }
        }
        if (rejectRunEnd && event.type().equals("run/end")) {
            throw new IllegalStateException("实验末尾导出失败");
        }
        accepted.add(event.eventId());
        if (failAtSequence.compareAndSet(event.seq(), -1)) {
            throw new IllegalStateException("实验确认丢失");
        }
    }
}
