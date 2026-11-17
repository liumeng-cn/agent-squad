package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 聊天公开快照订阅
 * <p>
 * 同一用户和会话共享原生日志读取，游标变化时重建公开快照；订阅取消不会取消 SDK 执行。
 */
@Service
public class ChatEventStreamService {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration DISCONNECT_GRACE = Duration.ZERO;
    private final ConcurrentMap<String, Flux<ChatSessionVO>> streams = new ConcurrentHashMap<>();

    @Resource
    private ChatSessionService sessionService;

    /**
     * 每次连接先获得完整快照，后续快照携带双游标，断线后无需重新提交原输入。
     * 原始日志读取只发生在提交位置或执行租约有效性变化后。
     */
    public Flux<ServerSentEvent<ChatSessionVO>> subscribe(String sessionId, String userId) {
        String key = userId + ":" + sessionId;
        Flux<ServerSentEvent<ChatSessionVO>> snapshots = Flux.defer(() -> streams.computeIfAbsent(key, ignored -> Flux.defer(() -> {
                            var session = sessionService.open(sessionId, userId);
                            return Flux.interval(Duration.ZERO, POLL_INTERVAL)
                                    .onBackpressureLatest()
                                    .publishOn(Schedulers.boundedElastic(), 1)
                                    .map(tick -> {
                                        var head = session.log().head();
                                        return head.seq() + ":" + session.log().inbox().head().seq() + ":"
                                                + (head.owner() != null && head.leaseUntil() > System.currentTimeMillis());
                                    })
                                    .distinctUntilChanged()
                                    .map(version -> sessionService.read(sessionId, userId));
                        })
                        .doFinally(signal -> streams.remove(key))
                        .replay(1)
                        .refCount(1, DISCONNECT_GRACE)))
                .map(snapshot -> ServerSentEvent.builder(snapshot)
                        .id(snapshot.getCursor() + ":" + snapshot.getInputCursor()).build());

        var heartbeats = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<ChatSessionVO>builder().comment("heartbeat")
                        .build());

        return Flux.merge(snapshots, heartbeats)
                .onBackpressureLatest();
    }
}
