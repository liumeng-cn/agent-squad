package com.agentsquad.squad.service;

import com.agentsquad.squad.properties.RedisWakeupProperties;
import com.agentsquad.squad.bo.RuntimeEventSourceBO;
import com.agentsquad.squad.mapper.ProjectionMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.agentsquad.squad.enums.RedisWakeupChannel;

/**
 * 已提交原生收件的Redis投影唤醒
 * <p>
 * 同一Binding合并通知，消费从MySQL游标补读官方journal。Redis确认不推进SDK确认位置，
 * 丢通知或尾部未导出仍由既有独立核对恢复，不依赖页面连接或模型会话继续存活。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agentsquad.redis-wakeup", name = "enabled", havingValue = "true")
public class RedisProjectionWakeup {
    private static final String GROUP = "projection-v1";
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(5);
    private final String consumer = UUID.randomUUID().toString();
    private final AtomicBoolean groupReady = new AtomicBoolean();
    private final AtomicBoolean unavailable = new AtomicBoolean();
    private final AtomicLong retryAfter = new AtomicLong();
    private final DefaultRedisScript<List> enqueue = new DefaultRedisScript<>();
    private final DefaultRedisScript<String> finish = new DefaultRedisScript<>();

    @Resource
    private RedisWakeupProperties properties;

    @Resource
    private StringRedisTemplate redis;

    @Resource
    private ProjectionMapper projections;

    @Resource
    private ObjectProvider<RuntimeProjectionDispatcher> dispatcher;

    @PostConstruct
    public void initialize() throws IOException {
        enqueue.setScriptText(new ClassPathResource("redis/command-enqueue.lua").getContentAsString(StandardCharsets.UTF_8));
        enqueue.setResultType(List.class);
        finish.setScriptText(new ClassPathResource("redis/command-finish.lua").getContentAsString(StandardCharsets.UTF_8));
        finish.setResultType(String.class);
    }

    /**
     * 收件已提交才发布；短暂退避避免Redis故障时每条原生事件都等待网络超时。
     */
    public boolean publish(RuntimeEventSourceBO source) {
        if (backingOff()) {
            return false;
        }
        try {
            redis.execute(enqueue, List.of(state(source.bindingId()), stream()),
                    String.valueOf(properties.getCapacity()), "1", RedisWakeupChannel.PROJECTION.name(),
                    source.userId(), source.bindingId(), "", "", source.sessionId(), String.valueOf(source.mappingVersion()));
            recovered();
            return true;
        } catch (RuntimeException failure) {
            failed(failure);
            return false;
        }
    }

    /**
     * 有界领取与pending接管；业务失败不确认，最终仍由持久收件定位故障和控制重试。
     */
    @Scheduled(fixedDelayString = "${agentsquad.redis-wakeup.projection-poll-delay-ms:200}")
    public void receive() {
        if (dispatcher.getIfAvailable() == null || backingOff()) {
            return;
        }
        try {
            ensureGroup();
            var pending = streams().pending(stream(), GROUP, Range.unbounded(), 16);
            for (var item : pending) {
                if (item.getElapsedTimeSinceLastDelivery().toMillis() >= properties.getReclaimIdleMs()) {
                    streams().claim(stream(), GROUP, consumer, Duration.ofMillis(properties.getReclaimIdleMs()), item.getId())
                            .forEach(this::dispatch);
                }
            }
            var records = streams().read(Consumer.from(GROUP, consumer), StreamReadOptions.empty().count(16),
                    StreamOffset.create(stream(), ReadOffset.lastConsumed()));
            if (records != null) {
                records.forEach(this::dispatch);
            }
            recovered();
        } catch (RuntimeException failure) {
            failed(failure);
        }
    }

    private void dispatch(MapRecord<String, String, String> record) {
        var fields = record.getValue();
        String bindingId = fields.get("bindingId");
        String userId = fields.get("userId");
        if (bindingId == null || !bindingId.matches("[A-Za-z0-9-]{1,36}") || userId == null) {
            log.warn("Redis投影引用身份无效 streamId={}", record.getId());
            streams().acknowledge(stream(), GROUP, record.getId());
            streams().delete(stream(), record.getId());
            return;
        }
        var target = projections.findTarget(userId, bindingId);
        if (target == null || !"1".equals(fields.get("schemaVersion"))
                || !RedisWakeupChannel.PROJECTION.name().equals(fields.get("kind"))
                || !String.valueOf(RuntimeEventSourceBO.CURRENT_MAPPING_VERSION).equals(fields.get("mappingVersion"))
                || !target.getSessionId().equals(fields.get("sessionId"))) {
            log.warn("Redis投影引用归属校验未通过 bindingId={} streamId={}", bindingId, record.getId());
            acknowledge(record, null, false);
            return;
        }
        Object observed = redis.opsForHash().get(state(bindingId), "dirtyVersion");
        var source = new RuntimeEventSourceBO(userId, bindingId, target.getSessionId(), RuntimeEventSourceBO.CURRENT_MAPPING_VERSION);
        dispatcher.getObject().scheduleFromQueue(source,
                more -> acknowledge(record, observed == null ? "0" : observed.toString(), more));
    }

    private void acknowledge(MapRecord<String, String, String> record, String observed, boolean more) {
        try {
            var fields = record.getValue();
            redis.execute(finish, List.of(state(fields.get("bindingId")), stream()), GROUP,
                    record.getId().getValue(), observed == null ? "0" : observed,
                    observed == null ? "DROP" : (more ? "1" : "0"), "1", RedisWakeupChannel.PROJECTION.name(),
                    fields.get("userId"), fields.get("bindingId"), "", "", fields.getOrDefault("sessionId", ""),
                    fields.getOrDefault("mappingVersion", ""));
        } catch (RuntimeException failure) {
            failed(failure);
        }
    }

    private void ensureGroup() {
        if (groupReady.get()) {
            return;
        }
        try {
            redis.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
                    bytes("CREATE"), bytes(stream()), bytes(GROUP), bytes("0"), bytes("MKSTREAM")));
        } catch (org.springframework.dao.DataAccessException failure) {
            boolean exists = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                    exists = true;
                }
            }
            if (!exists) {
                throw failure;
            }
        }
        groupReady.set(true);
    }

    private StreamOperations<String, String, String> streams() {
        return redis.opsForStream();
    }

    private String stream() {
        return properties.getNamespace() + ":{p0}:PROJECTION:ready";
    }

    private String state(String bindingId) {
        return properties.getNamespace() + ":{p0}:PROJECTION:binding:" + bindingId;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private void failed(RuntimeException failure) {
        groupReady.set(false);
        retryAfter.set(System.nanoTime() + FAILURE_BACKOFF.toNanos());
        if (unavailable.compareAndSet(false, true)) {
            log.warn("Redis投影唤醒不可用，转为本地调度与持久核对 errorType={}", failure.getClass().getSimpleName());
        }
    }

    private void recovered() {
        retryAfter.set(0);
        if (unavailable.compareAndSet(true, false)) {
            log.info("Redis投影唤醒已恢复");
        }
    }

    private boolean backingOff() {
        long next = retryAfter.get();
        return next != 0 && System.nanoTime() - next < 0;
    }
}
