package com.agentsquad.squad.observer;

import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import io.micrometer.tracing.Tracer;
import io.micrometer.context.ContextRegistry;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 持久执行链路追踪
 * <p>
 * 在受理事务中保存 W3C 父上下文，后台投递按原父上下文打开短生命周期 Span。
 */
@Component
public class ExecutionTrace {
    @Resource
    private ExecutionScopeMapper scopes;

    @Resource
    private Tracer tracer;

    @Resource
    private Propagator propagator;

    @PostConstruct
    public void registerReactorSpanAccessor() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ExecutionSpanAccessor(tracer));
    }

    /**
     * 在新执行范围受理时记录当前请求上下文，只持久化 W3C Trace 字段。
     */
    public void capture(Long userId, Long scopeId) {
        var current = tracer.currentSpan();
        if (current == null) {
            return;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(current.context(), carrier, Map::put);
        String parent = carrier.get("traceparent");
        if (parent != null) {
            scopes.saveTrace(userId, scopeId, parent, carrier.get("tracestate"));
        }
    }

    /**
     * 命令领取和投递共用持久父上下文，异常与正常退出均关闭线程内关联。
     */
    public void deliver(Long commandId, Runnable action) {
        run(scopes.commandTrace(commandId), "squad.command.deliver", "squad.command.id", String.valueOf(commandId), action);
    }

    /**
     * 将一次真实事件批次及其提交日志关联到原始受理链路。
     */
    public void project(Long userId, Long bindingId, Runnable action) {
        run(scopes.bindingTrace(userId, bindingId), "squad.journal.apply", "squad.binding.id", String.valueOf(bindingId), action);
    }

    /**
     * 每次订阅单独创建 Span，结束、失败与取消都以实际订阅生命周期为准。
     */
    public <T> Flux<T> session(Long userId, String sessionId, String name, Supplier<Flux<T>> action) {
        return Mono.fromCallable(() -> Optional.ofNullable(scopes.sessionTrace(userId, sessionId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(parent -> {
                    if (parent.isEmpty()) {
                        return Flux.defer(action);
                    }
                    var span = propagator.extract(parent.get(), Map::get).name(name).start();
                    span.tag("squad.session.id", sessionId);
                    return Flux.defer(action)
                            .doOnError(failure -> span.error(ObservationSupport.safeDiagnostic(failure)))
                            .doFinally(signal -> {
                                span.tag("squad.outcome", signal.name());
                                span.end();
                            })
                            .contextWrite(context -> context.put(ExecutionSpanAccessor.KEY, span));
                });
    }

    private void run(Map<String, String> carrier, String name, String key, String id, Runnable action) {
        if (carrier == null || carrier.get("traceparent") == null) {
            action.run();
            return;
        }
        var span = propagator.extract(carrier, Map::get).name(name).start();
        span.tag(key, id);
        try (var ignored = tracer.withSpan(span)) {
            action.run();
        } catch (RuntimeException failure) {
            span.error(ObservationSupport.safeDiagnostic(failure));
            throw failure;
        } finally {
            span.end();
        }
    }
}
