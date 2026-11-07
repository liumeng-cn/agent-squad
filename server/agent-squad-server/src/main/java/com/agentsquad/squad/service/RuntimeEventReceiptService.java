package com.agentsquad.squad.service;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeJournalReadySignal;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxStatus;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventFingerprint;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxRow;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxMapper;

import io.agentscope.core.session.SessionEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * 运行集成模块的原生事件持久收件服务
 * <p>
 * 短事务只核对归属、写安全定位和检查幂等；不执行模型、工具、业务投影或公开消息发布。
 */
@Service
public class RuntimeEventReceiptService {
    /**
     * SDK按原生会话分别记录此固定名称的确认游标。
     */
    public static final String SINK_NAME = "squad-execution-v2";
    private static final int MAX_NATIVE_HEADER_LENGTH = 160;

    @Resource
    private org.springframework.context.ApplicationEventPublisher signals;

    @Resource
    private RuntimeEventInboxMapper inboxMapper;

    /**
     * 先验证真实绑定，再幂等收件；任何失败回滚并向SDK抛出，事务提交后调用者才能确认导出。
     * 同序号或同事件ID异内容不覆盖，重投已消费事件也不回退其消费状态。
     */
    @Transactional
    public void receive(RuntimeEventSource source, SessionEvent event) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(event);
        validateEnvelope(event);
        if (!source.sessionId().equals(inboxMapper.lockBoundSession(source))) {
            throw new IllegalStateException("原生事件会话归属不匹配");
        }

        var incoming = new RuntimeEventInboxRow(UUID.randomUUID().toString(), source.userId(),
                source.bindingId(), source.sessionId(), event.seq(), event.eventId(), event.type(),
                event.executionRunId(), event.turnId(), event.occurredAt(), event.required(),
                event.schemaVersion(), RuntimeEventFingerprint.of(event), source.mappingVersion(), RuntimeEventInboxStatus.RECEIVED);
        int inserted = inboxMapper.insertIfAbsent(incoming);
        var collisions = inboxMapper.findCollisions(incoming);
        if (collisions.size() != 1 || !sameFact(collisions.getFirst(), incoming)) {
            throw new IllegalStateException("原生事件身份或内容冲突，需核对日志");
        }
        if (inserted == 1) {
            signals.publishEvent(new RuntimeJournalReadySignal(source));
        }
    }

    private boolean sameFact(RuntimeEventInboxRow stored, RuntimeEventInboxRow incoming) {
        return stored.nativeSeq() == incoming.nativeSeq()
                && stored.nativeEventId().equals(incoming.nativeEventId())
                && stored.sessionId().equals(incoming.sessionId())
                && stored.mappingVersion() == incoming.mappingVersion()
                && stored.payloadDigest().equals(incoming.payloadDigest());
    }

    private void validateEnvelope(SessionEvent event) {
        requireHeader(event.eventId(), false);
        requireHeader(event.type(), false);
        requireHeader(event.executionRunId(), true);
        requireHeader(event.turnId(), true);
    }

    private void requireHeader(String value, boolean nullable) {
        if (value == null && nullable) {
            return;
        }
        if (value == null || value.isBlank() || value.length() > MAX_NATIVE_HEADER_LENGTH) {
            throw new IllegalArgumentException("原生事件信封字段无效");
        }
    }

}
