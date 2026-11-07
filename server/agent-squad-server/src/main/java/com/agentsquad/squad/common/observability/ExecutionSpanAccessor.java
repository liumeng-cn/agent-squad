package com.agentsquad.squad.common.observability;

import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Reactor 执行 Span 的线程范围
 * <p>
 * 每次设置（包括清空）都打开独立范围，退出时按嵌套顺序恢复原范围。
 */
final class ExecutionSpanAccessor implements ThreadLocalAccessor<Span> {
    static final String KEY = ExecutionSpanAccessor.class.getName();

    private final Tracer tracer;
    private final ThreadLocal<Deque<Tracer.SpanInScope>> scopes = ThreadLocal.withInitial(ArrayDeque::new);

    ExecutionSpanAccessor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Span getValue() {
        return tracer.currentSpan();
    }

    @Override
    public void setValue(Span span) {
        scopes.get().push(tracer.withSpan(span));
    }

    @Override
    public void setValue() {
        setValue(null);
    }

    @Override
    public void restore(Span previousValue) {
        restore();
    }

    @Override
    public void restore() {
        var stack = scopes.get();
        try {
            var scope = stack.poll();
            if (scope != null) {
                scope.close();
            }
        } finally {
            if (stack.isEmpty()) {
                scopes.remove();
            }
        }
    }
}
