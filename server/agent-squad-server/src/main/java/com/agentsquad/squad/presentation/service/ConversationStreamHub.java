package com.agentsquad.squad.presentation.service;

import com.agentsquad.squad.domain.dto.ReplyPreview;
import com.agentsquad.squad.identity.UserStatusGuard;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同一用户会话的共享事件读取器
 * <p>
 * 多连接共享提交后读取，限制每轮批次；慢连接只丢弃自身订阅，不阻塞模型或业务提交。
 */
public final class ConversationStreamHub {
    private static final int EVENT_PAGE_SIZE = 100;
    private static final int MAX_BATCHES_PER_DRAIN = 4;
    private final ConversationStreamKey key;
    private final ConversationQueryService queries;
    private final UserStatusGuard users;
    private final Executor executor;
    private final ConcurrentHashMap<String, ConversationSubscriber> subscribers = new ConcurrentHashMap<>();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();

    public ConversationStreamHub(ConversationStreamKey key, ConversationQueryService queries,
                                 UserStatusGuard users, Executor executor) {
        this.key = key;
        this.queries = queries;
        this.users = users;
        this.executor = executor;
    }

    public void add(String id, ConversationSubscriber subscriber) {
        subscribers.put(id, subscriber);
    }

    public void remove(String id) {
        subscribers.remove(id);
    }

    public boolean isEmpty() {
        return subscribers.isEmpty();
    }

    public void wake() {
        dirty.set(true);
        if (subscribers.isEmpty() || !queued.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::drain);
        } catch (RejectedExecutionException busy) {
            queued.set(false);
            close();
        }
    }

    private void drain() {
        try {
            int batches = 0;
            do {
                dirty.set(false);
                users.requireActive(key.userId());
                var oldest = subscribers.values().stream().filter(subscriber -> !subscriber.isClosed())
                        .min(Comparator.comparingLong(subscriber -> sequence(subscriber.cursor()))).orElse(null);
                if (oldest == null) {
                    return;
                }
                var events = queries.events(key.userId(), key.conversationId(), oldest.cursor());
                for (var event : events) {
                    for (var subscriber : subscribers.values()) {
                        subscriber.offer(event);
                    }
                }
                if (events.size() >= EVENT_PAGE_SIZE) {
                    dirty.set(true);
                }
                batches++;
            } while (dirty.get() && !subscribers.isEmpty() && batches < MAX_BATCHES_PER_DRAIN);
        } catch (Exception unavailable) {
            close();
        } finally {
            queued.set(false);
            if (dirty.get() && !subscribers.isEmpty()) {
                wake();
            }
        }
    }

    private long sequence(String cursor) {
        return Long.parseLong(cursor.substring(cursor.lastIndexOf(':') + 1));
    }

    public void preview(ReplyPreview preview) {
        subscribers.values().forEach(subscriber -> subscriber.preview(preview));
    }

    public void heartbeatAndReconcile() {
        for (var subscriber : subscribers.values()) {
            subscriber.heartbeat();
        }
        wake();
    }

    public void close() {
        for (var subscriber : subscribers.values()) {
            subscriber.close();
        }
    }
}
