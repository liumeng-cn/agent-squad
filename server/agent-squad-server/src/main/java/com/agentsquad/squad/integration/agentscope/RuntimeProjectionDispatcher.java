package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventConsumptionService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxMapper;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeJournalReadySignal;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 已提交原生收件的合并投影调度
 * <p>
 * 每绑定只安排一个补读任务，通知只作唤醒，权威游标仍在数据库；不创建Worker、不启动会话、不续租执行资格。
 * 队列饱和或进程崩溃由持久收件与原生补偿恢复，不把内存入队作为SDK确认。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class RuntimeProjectionDispatcher {
    @org.springframework.beans.factory.annotation.Value("${agentsquad.projection.wakeup-enabled:true}")
    private boolean wakeupEnabled;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_DIRTY_BINDINGS = 1024;
    private final Set<RuntimeEventSource> dirty = ConcurrentHashMap.newKeySet();
    private final Set<RuntimeEventSource> scheduled = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), runnable -> {
        Thread thread = new Thread(runnable, "runtime-projection");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private ProjectionMapper projections;

    @Resource
    private NativeSessionGateway gateway;

    @Resource
    private RuntimeEventInboxMapper inbox;

    @Resource
    private com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventReceiptService receipts;

    @Resource
    private com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventFailureService failures;

    @Resource
    private RuntimeEventConsumptionService consumption;

    /**
     * 提交线程只合并唤醒，所有日志读取和业务事务在独立有界线程池执行。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(RuntimeJournalReadySignal signal) {
        if (!wakeupEnabled) {
            return;
        }
        RuntimeEventSource source = signal.source();
        if (dirty.size() >= MAX_DIRTY_BINDINGS && !dirty.contains(source)) {
            return;
        }
        dirty.add(source);
        schedule(source);
    }

    private void schedule(RuntimeEventSource source) {
        if (!scheduled.add(source)) {
            return;
        }
        try {
            executor.execute(() -> drain(source));
        } catch (RejectedExecutionException busy) {
            scheduled.remove(source);
        }
    }

    private void drain(RuntimeEventSource source) {
        boolean failed = false;
        try {
            dirty.remove(source);
            for (int page = 0; page < 4; page++) {
                var target = projections.findTarget(source.userId(), source.bindingId());
                if (target == null || !source.sessionId().equals(target.sessionId())) {
                    return;
                }
                var first = inbox.findBySequence(source, target.cursor() + 1);
                if (first != null && first.status() == com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxStatus.BLOCKED) {
                    return;
                }
                var events = gateway.readCommittedEvents(target, target.cursor(), BATCH_SIZE);
                if (events.isEmpty()) {
                    return;
                }
                for (var event : events) {
                    if (inbox.findBySequence(source, event.seq()) == null) {
                        receipts.receive(source, event);
                    }
                }
                consumption.consume(target, events);
                if (events.size() < BATCH_SIZE) {
                    return;
                }
            }
            dirty.add(source);
        } catch (Exception failure) {
            failed = true;
            dirty.add(source);
            if (failure instanceof RuntimeEventConsumptionException invalid) {
                try {
                    failures.record(source, invalid.sequence(), invalid.code());
                } catch (RuntimeException unavailable) {
                    log.warn("原生消费故障记录暂未保存 bindingId={} sourceSeq={} errorType={}",
                            source.bindingId(), invalid.sequence(), unavailable.getClass().getSimpleName(), unavailable);
                }
            }
            log.warn("原生事件主动投影失败，等待持久补偿 bindingId={} errorType={}",
                    source.bindingId(), failure.getClass().getSimpleName(), failure);
        } finally {
            scheduled.remove(source);
            if (!failed && dirty.contains(source)) {
                schedule(source);
            }
        }
    }

    /**
     * 队列饱和与失败不立即自旋；低频重试只处理已经提交的事实。
     */
    @Scheduled(fixedDelayString = "${agentsquad.projection.wakeup-retry-delay-ms:15000}")
    public void retryPending() {
        if (!wakeupEnabled) {
            return;
        }
        for (var source : inbox.pendingSources(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            if (dirty.size() < MAX_DIRTY_BINDINGS) {
                dirty.add(source);
            }
        }
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        // 尾部核对不以业务终态或仍有RECEIVED为前提；SDK最后一次导出可能早于最终flush。
        for (var target : projections.findReconciliationTargets(now)) {
            if (dirty.size() >= MAX_DIRTY_BINDINGS) {
                break;
            }
            projections.defer(target.userId(), target.bindingId(), now.plusSeconds(15));
            dirty.add(new RuntimeEventSource(target.userId(), target.bindingId(), target.sessionId(),
                    RuntimeEventSource.CURRENT_MAPPING_VERSION));
        }
        dirty.forEach(this::schedule);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
