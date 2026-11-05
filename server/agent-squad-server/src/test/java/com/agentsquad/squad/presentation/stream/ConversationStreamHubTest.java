package com.agentsquad.squad.presentation.stream;

import com.agentsquad.squad.identity.UserStatusGuard;
import com.agentsquad.squad.presentation.dto.ConversationEvent;
import com.agentsquad.squad.presentation.service.ConversationQueryService;
import com.agentsquad.squad.presentation.service.ConversationStreamHub;
import com.agentsquad.squad.presentation.service.ConversationStreamKey;
import com.agentsquad.squad.presentation.service.ConversationSubscriber;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话共享推送及连接隔离验证
 * <p>
 * 多连接只触发一次事件读取，通知竞争不丢失；慢连接和关闭仅释放自身订阅。
 */
class ConversationStreamHubTest {
    @Test
    void twoSubscribersShareOneCommittedEventRead() {
        var queries = mock(ConversationQueryService.class);
        var users = mock(UserStatusGuard.class);
        when(queries.events(eq("user"), eq("conversation"), anyString())).thenReturn(List.of(event(1)));
        var hub = new ConversationStreamHub(new ConversationStreamKey("user", "conversation"), queries, users, Runnable::run);
        var first = new RecordingSseEmitter();
        var second = new RecordingSseEmitter();
        hub.add("first", subscriber(first, Runnable::run, () -> {
        }));
        hub.add("second", subscriber(second, Runnable::run, () -> {
        }));
        hub.wake();
        assertThat(first.events).hasSize(1);
        assertThat(second.events).hasSize(1);
        verify(queries, times(1)).events("user", "conversation", "conversation:1:0");
    }

    @Test
    void coalescedNotificationDuringReadIsNotLost() {
        var queries = mock(ConversationQueryService.class);
        var users = mock(UserStatusGuard.class);
        var executor = new QueuedExecutor();
        var hub = new ConversationStreamHub(new ConversationStreamKey("user", "conversation"), queries, users, executor);
        var calls = new AtomicInteger();
        when(queries.events(eq("user"), eq("conversation"), anyString())).thenAnswer(call -> {
            int count = calls.incrementAndGet();
            if (count == 1) {
                hub.wake();
                return List.of(event(1));
            }
            return List.of(event(2));
        });
        var emitter = new RecordingSseEmitter();
        hub.add("one", subscriber(emitter, Runnable::run, () -> {
        }));
        hub.wake();
        hub.wake();
        hub.wake();
        assertThat(executor.tasks).hasSize(1);
        executor.runNext();
        assertThat(calls).hasValue(2);
        assertThat(emitter.events.stream().map(ConversationEvent::sequence)).containsExactly(1L, 2L);
        assertThat(executor.tasks).isEmpty();
    }

    @Test
    void slowSubscriberClosesOnlyItsOwnBoundedQueue() {
        var delayed = new QueuedExecutor();
        var slowEmitter = new RecordingSseEmitter();
        var fastEmitter = new RecordingSseEmitter();
        var released = new AtomicInteger();
        var slow = subscriber(slowEmitter, delayed, released::incrementAndGet);
        var fast = subscriber(fastEmitter, Runnable::run, () -> {
        });
        for (int index = 1; index <= 129; index++) {
            slow.offer(event(index));
            fast.offer(event(index));
        }
        assertThat(slow.isClosed()).isTrue();
        assertThat(fast.isClosed()).isFalse();
        assertThat(fastEmitter.events).hasSize(129);
        assertThat(released).hasValue(1);
        slow.close();
        assertThat(released).hasValue(1);
    }

    @Test
    void closedSubscriptionDoesNotReceiveFurtherEvents() {
        var emitter = new RecordingSseEmitter();
        var released = new AtomicInteger();
        var subscription = subscriber(emitter, Runnable::run, released::incrementAndGet);
        subscription.offer(event(1));
        subscription.close();
        subscription.offer(event(2));
        assertThat(emitter.events).hasSize(1);
        assertThat(released).hasValue(1);
    }

    @Test
    void unsupportedGapClosesConnectionWithoutInventingMissingEvents() {
        var emitter = new RecordingSseEmitter();
        var subscription = subscriber(emitter, Runnable::run, () -> {
        });
        subscription.offer(event(2));
        assertThat(subscription.isClosed()).isTrue();
        assertThat(emitter.events).isEmpty();
    }

    private ConversationSubscriber subscriber(RecordingSseEmitter emitter, java.util.concurrent.Executor sender, Runnable release) {
        return new ConversationSubscriber(emitter, "conversation:1:0", sender, release, () -> {
        });
    }

    private ConversationEvent event(long sequence) {
        return new ConversationEvent("conversation", 1, sequence, "message.completed", "2026-11-04T00:00:00Z",
                JsonNodeFactory.instance.objectNode());
    }
}
