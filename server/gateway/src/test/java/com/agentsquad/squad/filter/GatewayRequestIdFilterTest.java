package com.agentsquad.squad.filter;


import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关请求标识过滤器的复用、重建与 Reactor 上下文写入验证。
 */
class GatewayRequestIdFilterTest {

    private static final String VALID_REQUEST_ID = "22222222-2222-2222-2222-222222222222";

    @Test
    void preservesValidRequestIdForDownstreamAndContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/conversations")
                        .header(GatewayRequestIdFilter.REQUEST_ID_HEADER, VALID_REQUEST_ID).build());
        AtomicReference<String> forwarded = new AtomicReference<>();
        AtomicReference<Object> contextValue = new AtomicReference<>();
        WebFilterChain chain = current -> Mono.deferContextual(context -> {
            forwarded.set(current.getRequest().getHeaders().getFirst(GatewayRequestIdFilter.REQUEST_ID_HEADER));
            contextValue.set(context.getOrEmpty(GatewayRequestIdFilter.REQUEST_ID_CONTEXT_KEY).orElse(null));
            return Mono.empty();
        });

        new GatewayRequestIdFilter().filter(exchange, chain).block();

        assertEquals(VALID_REQUEST_ID, forwarded.get());
        assertEquals(VALID_REQUEST_ID, contextValue.get());
        assertEquals(VALID_REQUEST_ID,
                exchange.getResponse().getHeaders().getFirst(GatewayRequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void replacesForgedRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/conversations")
                        .header(GatewayRequestIdFilter.REQUEST_ID_HEADER, "forged").build());
        AtomicReference<String> forwarded = new AtomicReference<>();
        WebFilterChain chain = current -> Mono.fromRunnable(() ->
                forwarded.set(current.getRequest().getHeaders().getFirst(GatewayRequestIdFilter.REQUEST_ID_HEADER)));

        new GatewayRequestIdFilter().filter(exchange, chain).block();

        assertNotEquals("forged", forwarded.get());
        assertTrue(forwarded.get().matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        assertEquals(forwarded.get(),
                exchange.getResponse().getHeaders().getFirst(GatewayRequestIdFilter.REQUEST_ID_HEADER));
    }
}
