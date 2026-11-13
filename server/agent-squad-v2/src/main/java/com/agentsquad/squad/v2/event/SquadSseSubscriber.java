package com.agentsquad.squad.v2.event;

import com.agentsquad.squad.common.model.SquadMsg;
import com.agentsquad.squad.v2.bo.SquadExecutionBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE 订阅器；连接断开只释放订阅，不向 AgentRun 传播取消信号。
 */
@Slf4j
@Component
public class SquadSseSubscriber {
    /**
     * 按 afterSequence 补收并持续接收后续事件。
     */
    public void subscribe(SquadExecutionBO execution, SseEmitter emitter, Long afterSequence) {
        AtomicBoolean detached = new AtomicBoolean(false);
        AtomicReference<Disposable> ref = new AtomicReference<>();
        Runnable detach = () -> {
            if (detached.compareAndSet(false, true)) {
                Disposable disposable = ref.get();
                if (disposable != null) disposable.dispose();
            }
        };
        emitter.onCompletion(detach);
        emitter.onTimeout(() -> {
            detach.run();
            emitter.complete();
        });
        emitter.onError(error -> detach.run());
        try {
            Disposable disposable = execution.getEventBuffer().subscribeAfter(afterSequence)
                    .publishOn(Schedulers.boundedElastic()).subscribe(
                            event -> send(execution, emitter, event, detach),
                            error -> {
                                if (!detached.get()) emitter.completeWithError(error);
                            },
                            () -> {
                                if (!detached.get()) emitter.complete();
                            });
            ref.set(disposable);
            if (detached.get()) disposable.dispose();
        } catch (RuntimeException error) {
            emitter.completeWithError(error);
        }
    }

    private void send(SquadExecutionBO execution, SseEmitter emitter, SquadMsg event, Runnable detach) {
        try {
            emitter.send(SseEmitter.event().id(String.valueOf(event.getSequence()))
                    .name("squad").data(event));
        } catch (IOException | IllegalStateException disconnected) {
            log.debug("V2 SSE 客户端断开，后台执行继续，executionId={}", execution.getExecutionId());
            detach.run();
        }
    }
}