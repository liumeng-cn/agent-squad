package com.agentsquad.squad.gateway.common;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * 阻塞调用的 Reactor 适配。
 *
 * <p>JDBC、BCrypt 等阻塞操作必须经 boundedElastic 执行，禁止占用 Netty event-loop。</p>
 */
public final class BlockingCalls {

    private BlockingCalls() {
    }

    public static <T> Mono<T> mono(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    public static Mono<Void> run(Runnable runnable) {
        return mono(() -> {
            runnable.run();
            return Boolean.TRUE;
        }).then();
    }
}
