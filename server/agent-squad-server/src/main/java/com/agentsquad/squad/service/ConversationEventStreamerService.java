package com.agentsquad.squad.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.domain.model.ConversationCommittedSignal;
import com.agentsquad.squad.domain.model.ConversationStreamKey;
import com.agentsquad.squad.domain.model.ReplyPreviewSignal;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 提交后主动推送的会话SSE服务
 *
 * 同一用户会话共享事件读取，连接只拥有独立有界发送队列；关闭页面不停止后台任务或消费。
 */
@Service
public class ConversationEventStreamerService {
    private static final int EXECUTOR_BACKLOG = 128;

    @Resource
    private ConversationQueryService queries;

    @Resource
    private UserStatusGuardService userStatus;

    @Value("${agentsquad.events.poll-threads:4}")
    private int pollThreads;

    @Value("${agentsquad.events.sender-threads:8}")
    private int senderThreads;

    @Value("${agentsquad.events.max-connections:100}")
    private int maxConnections;

    @Value("${agentsquad.events.max-connections-per-user:5}")
    private int maxConnectionsPerUser;

    private final ConcurrentHashMap<String, Integer> userConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ConversationStreamKey, ConversationStreamHubService> hubs = new ConcurrentHashMap<>();
    private ThreadPoolExecutor readers;
    private ThreadPoolExecutor senders;
    private Semaphore slots;

    @PostConstruct
    public void initialize() {
        if (pollThreads < 1 || senderThreads < 1 || maxConnections < 1 || maxConnectionsPerUser < 1) {
            throw new IllegalArgumentException("会话订阅配置无效");
        }
        slots = new Semaphore(maxConnections);
        readers = executor(pollThreads, "conversation-event-read");
        senders = executor(senderThreads, "conversation-event-send");
    }

    private ThreadPoolExecutor executor(int threads, String name) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(EXECUTOR_BACKLOG), runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** 验证持久游标后订阅；首次补读与并发提交通过连续事件水位去重衔接。 */
    public SseEmitter stream(String userId, String conversationId, String cursor) {
        userStatus.requireActive(userId);
        var initial = queries.events(userId, conversationId, cursor);
        String after = cursor;
        if (after == null || after.isBlank()) {
            var header = queries.requireHeader(userId, conversationId);
            after = conversationId + ":" + header.getGeneration() + ":0";
        }
        if (!slots.tryAcquire()) {
            throw busy();
        }
        int count = userConnections.compute(userId, (key, existing) -> existing == null ? 1 : existing + 1);
        if (count > maxConnectionsPerUser) {
            release(userId);
            throw busy();
        }

        var key = new ConversationStreamKey(userId, conversationId);
        String id = UUID.randomUUID().toString();
        var emitter = new SseEmitter(0L);
        var subscriber = new ConversationSubscriberService(emitter, after, senders, () -> {
            hubs.computeIfPresent(key, (ignored, hub) -> {
                hub.remove(id);
                return hub.isEmpty() ? null : hub;
            });
            release(userId);
        }, () -> {
            userStatus.requireActive(userId);
            queries.requireHeader(userId, conversationId);
        });
        try {
            var hub = hubs.compute(key, (ignored, existing) -> {
                var selected = existing == null ? new ConversationStreamHubService(key, queries, userStatus, readers) : existing;
                selected.add(id, subscriber);
                return selected;
            });
            initial.forEach(subscriber::offer);
            subscriber.heartbeat();
            hub.wake();
        } catch (RuntimeException unavailable) {
            subscriber.close();
            throw unavailable;
        }
        return emitter;
    }

    /** 业务事务已提交才唤醒读取；提交线程不执行网络发送，不依赖是否存在页面连接。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(ConversationCommittedSignal signal) {
        wake(signal);
    }

    /** 本地提交和跨实例提示共用读取入口；每批仍由查询服务验证用户及会话归属。 */
    public void wake(ConversationCommittedSignal signal) {
        var hub = hubs.get(new ConversationStreamKey(signal.userId(), signal.conversationId()));
        if (hub != null) {
            hub.wake();
        }
    }

    /** 临时文本只进入订阅队列，不占持久序号，也不触发任务创建或取消。 */
    @org.springframework.context.event.EventListener
    public void preview(ReplyPreviewSignal signal) {
        var hub = hubs.get(new ConversationStreamKey(signal.userId(), signal.conversationId()));
        if (hub != null) {
            hub.preview(signal.preview());
        }
    }

    /** 低频会话级核对仅补漏；正常更新由提交通知触发，不再每连接500ms查库。 */
    @Scheduled(fixedDelayString = "${agentsquad.events.reconcile-delay-ms:15000}")
    public void reconcile() {
        hubs.values().forEach(ConversationStreamHubService::heartbeatAndReconcile);
    }

    private void release(String userId) {
        userConnections.compute(userId, (key, existing) -> existing == null || existing <= 1 ? null : existing - 1);
        slots.release();
    }

    private RuntimeBusinessException busy() {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "订阅连接暂时繁忙，请稍后重试");
    }

    @PreDestroy
    public void shutdown() {
        hubs.values().forEach(ConversationStreamHubService::close);
        if (readers != null) {
            readers.shutdownNow();
        }
        if (senders != null) {
            senders.shutdownNow();
        }
    }
}
