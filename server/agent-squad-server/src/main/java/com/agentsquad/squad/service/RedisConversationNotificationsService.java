package com.agentsquad.squad.service;

import com.agentsquad.squad.properties.RedisWakeupProperties;
import com.agentsquad.squad.event.ConversationCommittedSignal;
import com.agentsquad.squad.bo.ConversationWakeupNoticeBO;
import com.agentsquad.squad.bo.ConversationPreviewNoticeBO;
import com.agentsquad.squad.bo.ReplyPreviewBO;
import com.agentsquad.squad.event.ReplyPreviewSignal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 持久会话事件的跨实例通知
 * <p>
 * 提交后异步广播会话定位，订阅端只唤醒本机共享hub；通知可丢失，断线恢复与定期核对仍读持久事件。
 * 公开正文预览另走有界可丢通道，不广播模型上下文或内部推理，不让Redis网络操作阻塞业务提交线程。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agentsquad.redis-wakeup", name = "enabled", havingValue = "true")
public class RedisConversationNotificationsService {
    private static final int MAX_NOTICE_BYTES = 1024;
    private static final int MAX_PREVIEW_UNITS = 65536;
    private static final int MAX_PREVIEW_BYTES = MAX_PREVIEW_UNITS * 6 + MAX_NOTICE_BYTES;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean publishUnavailable = new AtomicBoolean();
    private final AtomicBoolean subscribeUnavailable = new AtomicBoolean();
    private final RedisMessageListenerContainer listener = new RedisMessageListenerContainer();
    private final ThreadPoolExecutor publisher = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), runnable -> {
        Thread thread = new Thread(runnable, "conversation-redis-notify");
        thread.setDaemon(true);
        return thread;
    });
    private final ThreadPoolExecutor previewPublisher = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
        Thread thread = new Thread(runnable, "conversation-redis-preview");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private RedisConnectionFactory connections;

    @Resource
    private StringRedisTemplate redis;

    @Resource
    private RedisWakeupProperties properties;

    @Resource
    private ObjectMapper json;

    @Resource
    private ConversationEventStreamerService streamer;

    @PostConstruct
    public void initialize() {
        listener.setConnectionFactory(connections);
        listener.setMaxSubscriptionRegistrationWaitingTime(500);
        listener.setRecoveryInterval(1000);
        listener.addMessageListener((message, pattern) -> receive(message.getBody()), new ChannelTopic(topic()));
        listener.addMessageListener((message, pattern) -> receivePreview(message.getBody()), new ChannelTopic(previewTopic()));
        listener.afterPropertiesSet();
        ensureSubscribed();
    }

    /**
     * 通知入有界线程队列，拒绝或发布失败仅影响即时性，不回滚答案、命令或预算。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(ConversationCommittedSignal signal) {
        try {
            publisher.execute(() -> publish(signal));
        } catch (RejectedExecutionException busy) {
            log.debug("跨实例会话通知队列已满，等待持久事件核对 conversationId={}", signal.conversationId());
        }
    }

    /**
     * 只转发输出适配器的公开正文，队列有界；丢弃预览不能取消模型或改变终稿。
     */
    @org.springframework.context.event.EventListener
    public void preview(ReplyPreviewSignal signal) {
        if (!validPreview(signal.preview())) {
            return;
        }
        try {
            previewPublisher.execute(() -> publishPreview(signal));
        } catch (RejectedExecutionException busy) {
            log.debug("跨实例正文预览队列已满 executionId={}", signal.preview().executionId());
        }
    }

    private void publishPreview(ReplyPreviewSignal signal) {
        try {
            var notice = new ConversationPreviewNoticeBO(1, instanceId, signal.userId(), signal.conversationId(), signal.preview());
            redis.convertAndSend(previewTopic(), json.writeValueAsString(notice));
        } catch (Exception failure) {
            // 只丢弃可替换预览，故障状态由持久事件通知发布路径记录，避免逐块刷屏。
            log.debug("跨实例正文预览未发送 executionId={} errorType={}", signal.preview().executionId(), failure.getClass().getSimpleName());
        }
    }

    private void receivePreview(byte[] payload) {
        if (payload.length > MAX_PREVIEW_BYTES) {
            log.warn("跨实例正文预览超出大小限制 bytes={}", payload.length);
            return;
        }
        try {
            var notice = json.readValue(payload, ConversationPreviewNoticeBO.class);
            if (notice.schemaVersion() != 1 || !identity(notice.instanceId())
                    || instanceId.equals(notice.instanceId()) || !identity(notice.userId())
                    || !identity(notice.conversationId()) || !validPreview(notice.preview())) {
                return;
            }
            // 不重新发布Spring事件；订阅发送前仍逐批验证用户与会话访问资格。
            streamer.preview(new ReplyPreviewSignal(notice.userId(), notice.conversationId(), notice.preview()));
        } catch (Exception invalid) {
            log.debug("跨实例正文预览无法读取 errorType={}", invalid.getClass().getSimpleName());
        }
    }

    private boolean validPreview(ReplyPreviewBO preview) {
        return preview != null && preview.schemaVersion() == 2 && preview.preview() && preview.replace()
                && preview.offset() == 0 && preview.revision() > 0 && identity(preview.executionId())
                && identity(preview.sourceMessageId()) && identity(preview.runId()) && identity(preview.streamId())
                && preview.text() != null && preview.text().length() <= MAX_PREVIEW_UNITS;
    }

    private void publish(ConversationCommittedSignal signal) {
        try {
            var notice = new ConversationWakeupNoticeBO(1, instanceId, signal.userId(), signal.conversationId());
            redis.convertAndSend(topic(), json.writeValueAsString(notice));
            if (publishUnavailable.compareAndSet(true, false)) {
                log.info("跨实例会话通知发布已恢复");
            }
        } catch (Exception failure) {
            if (publishUnavailable.compareAndSet(false, true)) {
                log.warn("跨实例会话通知暂不可用，持久事件核对继续 errorType={}", failure.getClass().getSimpleName());
            }
        }
    }

    private void receive(byte[] payload) {
        if (payload.length > MAX_NOTICE_BYTES) {
            log.warn("跨实例会话通知超出大小限制 bytes={}", payload.length);
            return;
        }
        try {
            var notice = json.readValue(new String(payload, StandardCharsets.UTF_8), ConversationWakeupNoticeBO.class);
            if (notice.schemaVersion() != 1 || !identity(notice.instanceId())
                    || !identity(notice.userId()) || !identity(notice.conversationId())
                    || instanceId.equals(notice.instanceId())) {
                return;
            }
            // 直接唤醒，不重新发布Spring事件，避免实例间广播回环。
            streamer.wake(new ConversationCommittedSignal(notice.userId(), notice.conversationId()));
        } catch (Exception invalid) {
            log.debug("跨实例会话通知无法读取 errorType={}", invalid.getClass().getSimpleName());
        }
    }

    /**
     * 首次订阅失败允许服务启动，后续重连；断线期间的通知由共享hub独立核对补齐。
     */
    @Scheduled(fixedDelayString = "${agentsquad.events.redis-reconnect-delay-ms:5000}")
    public void ensureSubscribed() {
        if (listener.isRunning()) {
            return;
        }
        try {
            listener.start();
            if (subscribeUnavailable.compareAndSet(true, false)) {
                log.info("跨实例会话通知订阅已恢复");
            }
        } catch (RuntimeException failure) {
            if (subscribeUnavailable.compareAndSet(false, true)) {
                log.warn("跨实例会话订阅暂不可用，定期核对继续 errorType={}", failure.getClass().getSimpleName());
            }
        }
    }

    private String topic() {
        return properties.getNamespace() + ":conversation-notify:v1";
    }

    private String previewTopic() {
        return properties.getNamespace() + ":conversation-preview:v1";
    }

    private boolean identity(String value) {
        return value != null && value.matches("[A-Za-z0-9-]{1,36}");
    }

    @PreDestroy
    public void shutdown() throws Exception {
        try {
            listener.destroy();
        } finally {
            publisher.shutdownNow();
            previewPublisher.shutdownNow();
        }
    }
}
