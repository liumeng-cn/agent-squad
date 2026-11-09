package com.agentsquad.squad.config;

import com.agentsquad.squad.filter.GatewayRequestIdFilter;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

/**
 * 网关观测上下文配置。
 * <p>
 * 将 requestId 注册为 Reactor 上下文与 MDC 之间的桥接项，异步切换线程时恢复对应值；
 * traceId/spanId 由 OpenTelemetry 事件监听器维护，不在此重复注册。
 */
@Configuration
public class GatewayObservationConfiguration {

    @PostConstruct
    public void registerRequestIdAccessor() {
        String key = GatewayRequestIdFilter.REQUEST_ID_CONTEXT_KEY;
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                key,
                () -> MDC.get(key),
                value -> MDC.put(key, value),
                () -> MDC.remove(key));
    }
}
