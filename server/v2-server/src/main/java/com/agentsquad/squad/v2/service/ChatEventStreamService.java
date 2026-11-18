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
    private ChatRuntimeService sessionService;

    /**
     * 每次连接先获得完整快照，后续快照携带双游标，断线后无需重新提交原输入。
     * 原始日志读取只发生在提交位置或执行租约有效性变化后。
     */
    public Flux<ServerSentEvent<ChatSessionVO>> subscribe(String sessionId, String userId) {
        if (sessionService.isPreview(sessionId)) {
            return Flux.just(ServerSentEvent.builder(sessionService.read(sessionId, userId)).build())
                    .concatWith(Flux.never());
        }
        String key = userId + ":" + sessionId;
        Flux<ServerSentEvent<ChatSessionVO>> snapshots = Flux.defer(() -> sharedStream(key, sessionId, userId))
                .map(this::snapshotEvent);
        var heartbeats = Flux.interval(Duration.ofSeconds(15)).map(this::heartbeatEvent);

        return Flux.merge(snapshots, heartbeats)
                .onBackpressureLatest();
    }

    /** 同一会话共享轮询和最近快照，最后一个订阅退出时释放 */
    private Flux<ChatSessionVO> sharedStream(String key, String sessionId, String userId) {
        return streams.computeIfAbsent(key, ignored -> createStream(key, sessionId, userId));
    }

    private Flux<ChatSessionVO> createStream(String key, String sessionId, String userId) {
        return Flux.defer(() -> pollSnapshots(sessionId, userId))
                .doFinally(signal -> streams.remove(key))
                .replay(1)
                .refCount(1, DISCONNECT_GRACE);
    }

    /** 在订阅时取得句柄，游标变化后才重建快照 */
    private Flux<ChatSessionVO> pollSnapshots(String sessionId, String userId) {
        var session = sessionService.open(sessionId, userId);
        return Flux.interval(Duration.ZERO, POLL_INTERVAL)
                .onBackpressureLatest()
                .publishOn(Schedulers.boundedElastic(), 1)
                .map(tick -> version(session))
                .distinctUntilChanged()
                .map(version -> sessionService.read(sessionId, userId));
    }

    private String version(io.agentscope.harness.agent.session.AgentSession session) {
        var head = session.log().head();
        boolean leaseActive = head.owner() != null && head.leaseUntil() > System.currentTimeMillis();
        return head.seq() + ":" + session.log().inbox().head().seq() + ":" + leaseActive;
    }

    private ServerSentEvent<ChatSessionVO> snapshotEvent(ChatSessionVO snapshot) {
        return ServerSentEvent.builder(snapshot)
                .id(snapshot.getCursor() + ":" + snapshot.getInputCursor()).build();
    }

    private ServerSentEvent<ChatSessionVO> heartbeatEvent(long tick) {
        return ServerSentEvent.<ChatSessionVO>builder().comment("heartbeat").build();
    }

}
