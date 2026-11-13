package com.agentsquad.squad.v2.event;

import static org.junit.jupiter.api.Assertions.*;
import com.agentsquad.squad.common.model.SquadMsg;
import com.agentsquad.squad.v2.exceptions.EventBufferExpiredException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** V2 事件缓冲与断线补收测试。 */
class SquadEventBufferTest {
    @Test void shouldReplayEventsAfterLastReceivedSequence() {
        SquadEventBuffer buffer = new SquadEventBuffer(100);
        for (long i = 1; i <= 3; i++) {
            SquadMsg msg = new SquadMsg();
            msg.setSequence(i);
            buffer.publish(msg);
        }
        buffer.complete();
        var events = buffer.subscribeAfter(1L).collectList().block(Duration.ofSeconds(1));
        assertEquals(2, events.size());
        assertEquals(2L, events.get(0).getSequence());
        assertEquals(3L, events.get(1).getSequence());
    }
    @Test void shouldRejectExpiredCursor() {
        SquadEventBuffer buffer = new SquadEventBuffer(100);
        for (long i = 1; i <= 101; i++) {
            SquadMsg msg = new SquadMsg();
            msg.setSequence(i);
            buffer.publish(msg);
        }
        assertThrows(EventBufferExpiredException.class, () -> buffer.subscribeAfter(0L));
    }
}