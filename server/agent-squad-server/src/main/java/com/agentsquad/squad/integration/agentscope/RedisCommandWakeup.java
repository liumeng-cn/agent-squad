package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.service.CommandDeliveryService;

import com.agentsquad.squad.config.RedisWakeupProperties;
import com.agentsquad.squad.domain.entity.CommandClaimContextDO;
import com.agentsquad.squad.domain.enums.RuntimeCommandAction;
import com.agentsquad.squad.mapper.DeliveryMapper;
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
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.agentsquad.squad.domain.enums.RedisWakeupChannel;

/**
 * Redis Streams命令唤醒适配
 * <p>
 * 事务提交后只写可信命令引用。消费仍经过原有数据库领取、授权及SDK幂等入口，
 * 投递尝试完成后确认引用；Redis丢失或延迟由持久命令扫描补齐，不控制后台模型生命周期。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agentsquad.redis-wakeup", name = "enabled", havingValue = "true")
public class RedisCommandWakeup {
    private static final String GROUP = "command-delivery-v1";
    private final String consumer = UUID.randomUUID().toString();
    private final AtomicBoolean unavailable = new AtomicBoolean();
    private final java.util.Set<RedisWakeupChannel> initializedGroups = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final DefaultRedisScript<List> enqueue = new DefaultRedisScript<>();
    private final DefaultRedisScript<String> finish = new DefaultRedisScript<>();

    @Resource
    private StringRedisTemplate redis;

    @Resource
    private RedisWakeupProperties properties;

    @Resource
    private DeliveryMapper commands;

    @Resource
    private ObjectProvider<CommandDeliveryService> delivery;

    @PostConstruct
    public void initialize() throws java.io.IOException {
        if (!properties.getNamespace().matches("[A-Za-z0-9:_-]{1,80}")
                || properties.getCapacity() < 1 || properties.getReclaimIdleMs() < 1000) {
            throw new IllegalArgumentException("Redis唤醒命名空间、容量或接管间隔无效");
        }
        enqueue.setScriptText(new ClassPathResource("redis/command-enqueue.lua").getContentAsString(StandardCharsets.UTF_8));
        enqueue.setResultType(List.class);
        finish.setScriptText(new ClassPathResource("redis/command-finish.lua").getContentAsString(StandardCharsets.UTF_8));
        finish.setResultType(String.class);
    }

    /**
     * 调用方已提交命令事务。发布失败返回false，由本地派发及低频数据库扫描接替。
     * 同一命令的重复提示合并，不合并同一绑定的不同命令，避免漏掉补充输入。
     */
    public boolean publish(String commandId) {
        try {
            CommandClaimContextDO owner = commands.claimContext(commandId);
            if (owner == null) {
                return false;
            }
            RedisWakeupChannel channel = channel(owner);
            redis.execute(enqueue, List.of(state(channel, commandId), stream(channel)),
                    String.valueOf(properties.getCapacity()), "1", channel.name(),
                    owner.getUserId(), owner.getBindingId(), commandId, "");
            recovered();
            return true;
        } catch (RuntimeException failure) {
            failed(failure);
            return false;
        }
    }

    /**
     * 控制通道先读，每批有界；容量不足的消息保持pending，接管后仍须数据库重新领取。
     */
    @Scheduled(fixedDelayString = "${agentsquad.redis-wakeup.poll-delay-ms:200}")
    public void receive() {
        if (delivery.getIfAvailable() == null) {
            return;
        }
        try {
            for (RedisWakeupChannel channel : List.of(RedisWakeupChannel.CONTROL, RedisWakeupChannel.COMMAND)) {
                ensureGroup(channel);
                var pending = streams().pending(stream(channel), GROUP, Range.unbounded(), 16);
                for (var item : pending) {
                    if (item.getElapsedTimeSinceLastDelivery().toMillis() >= properties.getReclaimIdleMs()) {
                        var claimed = streams().claim(stream(channel), GROUP, consumer,
                                Duration.ofMillis(properties.getReclaimIdleMs()), item.getId());
                        claimed.forEach(record -> dispatch(channel, record));
                    }
                }
                var records = streams().read(Consumer.from(GROUP, consumer), StreamReadOptions.empty().count(16),
                        StreamOffset.create(stream(channel), ReadOffset.lastConsumed()));
                if (records != null) {
                    records.forEach(record -> dispatch(channel, record));
                }
            }
            recovered();
        } catch (RuntimeException failure) {
            failed(failure);
        }
    }

    private void dispatch(RedisWakeupChannel channel, MapRecord<String, String, String> record) {
        String commandId = record.getValue().get("commandId");
        if (commandId == null || !commandId.matches("[A-Za-z0-9-]{1,100}")) {
            log.warn("Redis命令引用无效 streamId={} channel={}", record.getId(), channel);
            streams().acknowledge(stream(channel), GROUP, record.getId());
            streams().delete(stream(channel), record.getId());
            return;
        }
        CommandClaimContextDO owner = commands.claimContext(commandId);
        if (owner == null || !"1".equals(record.getValue().get("schemaVersion"))
                || !owner.getUserId().equals(record.getValue().get("userId"))
                || !owner.getBindingId().equals(record.getValue().get("bindingId")) || channel(owner) != channel) {
            log.warn("Redis命令引用归属校验未通过 commandId={} streamId={}", commandId, record.getId());
            acknowledge(channel, commandId, record, null);
            return;
        }
        Object observed = redis.opsForHash().get(state(channel, commandId), "dirtyVersion");
        delivery.getObject().scheduleFromQueue(commandId,
                () -> acknowledge(channel, commandId, record, observed == null ? "0" : observed.toString()));
    }

    private void acknowledge(RedisWakeupChannel channel, String commandId,
                             MapRecord<String, String, String> record, String observed) {
        try {
            var body = record.getValue();
            redis.execute(finish, List.of(state(channel, commandId), stream(channel)), GROUP,
                    record.getId().getValue(), observed == null ? "0" : observed, observed == null ? "DROP" : "0", "1", channel.name(),
                    body.getOrDefault("userId", ""), body.getOrDefault("bindingId", ""), commandId, "");
        } catch (RuntimeException failure) {
            // 业务投递已完成，不因ACK失败再次调用SDK；pending由租约及原命令幂等恢复。
            failed(failure);
        }
    }

    private void ensureGroup(RedisWakeupChannel channel) {
        if (initializedGroups.contains(channel)) {
            return;
        }
        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                    connection.execute("XGROUP", bytes("CREATE"), bytes(stream(channel)), bytes(GROUP), bytes("0"), bytes("MKSTREAM")));
        } catch (org.springframework.dao.DataAccessException failure) {
            if (!hasBusyGroup(failure)) {
                throw failure;
            }
        }
        initializedGroups.add(channel);
    }

    private boolean hasBusyGroup(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    private StreamOperations<String, String, String> streams() {
        return redis.opsForStream();
    }

    private RedisWakeupChannel channel(CommandClaimContextDO owner) {
        return RuntimeCommandAction.STOP.name().equals(owner.getAction()) || RuntimeCommandAction.RESPOND.name().equals(owner.getAction())
                ? RedisWakeupChannel.CONTROL : RedisWakeupChannel.COMMAND;
    }

    private String stream(RedisWakeupChannel channel) {
        return properties.getNamespace() + ":{p0}:" + channel.name() + ":ready";
    }

    private String state(RedisWakeupChannel channel, String commandId) {
        return properties.getNamespace() + ":{p0}:" + channel.name() + ":command:" + commandId;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private void failed(RuntimeException failure) {
        initializedGroups.clear();
        if (unavailable.compareAndSet(false, true)) {
            log.warn("Redis命令唤醒不可用，持久命令扫描继续执行 errorType={}", failure.getClass().getSimpleName());
        }
    }

    private void recovered() {
        if (unavailable.compareAndSet(true, false)) {
            log.info("Redis命令唤醒已恢复，持久命令扫描继续补偿");
        }
    }
}
