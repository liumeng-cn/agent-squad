package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.domain.CommandClaimContext;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 命令投递容量隔离测试。
 * <p>
 * 在投递入口阻塞用户 A，验证其他用户和控制命令仍有执行机会；不声称真实 SDK 线程安全。
 */
class CommandDeliveryConcurrencyTest {
    @Test
    void blockedSubmitDoesNotBlockOtherUserOrControlCommands() throws Exception {
        CommandDeliveryService service = spy(new CommandDeliveryService());
        DeliveryMapper mapper = mock(DeliveryMapper.class);
        ReflectionTestUtils.setField(service, "deliveryMapper", mapper);
        ReflectionTestUtils.setField(service, "workers", 2);
        ReflectionTestUtils.setField(service, "controlWorkers", 1);
        when(mapper.findDueIds(any())).thenReturn(List.of("A", "B", "STOP"), List.of("RESPOND"));
        when(mapper.claimContext(any())).thenAnswer(call -> new CommandClaimContext(
                "user-" + call.getArgument(0), "scope", call.getArgument(0), "binding"));
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherUser = new CountDownLatch(1);
        CountDownLatch stop = new CountDownLatch(1);
        CountDownLatch respond = new CountDownLatch(1);
        doAnswer(call -> {
            String id = call.getArgument(0);
            if ("A".equals(id)) {
                blocked.countDown();
                release.await(5, TimeUnit.SECONDS);
            } else if ("B".equals(id)) {
                otherUser.countDown();
            } else if ("STOP".equals(id)) {
                stop.countDown();
            } else if ("RESPOND".equals(id)) {
                respond.countDown();
            }
            return null;
        }).when(service).deliver(any());
        service.initializeDelivery();
        try {
            service.deliverDue();
            assertThat(blocked.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(otherUser.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stop.await(1, TimeUnit.SECONDS)).isTrue();
            // 无队列拒绝的控制命令保持未领取，下一轮重扫即可运行。
            service.deliverDue();
            assertThat(respond.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            service.shutdownDelivery();
        }
    }
}
