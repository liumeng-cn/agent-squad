package com.agentsquad.squad.integration.agentscope.inbox;

import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import com.agentsquad.squad.integration.agentscope.RuntimeEventConsumptionException;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionEventTypes;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 运行集成模块的有界事件消费服务
 * <p>
 * 先核对持久收件与官方日志一致，再让业务服务按既有锁序串行处理同一绑定；业务、游标和收件确认同事务提交。
 */
@Service
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class RuntimeEventConsumptionService {
    private static final int MAX_BATCH_SIZE = 100;

    @Resource
    private RuntimeEventInboxMapper inboxMapper;

    @Resource
    private RuntimeEventBusinessWriter businessWriter;

    /**
     * 单批任何缺号、冲突或业务失败均回滚整批；收件已独立提交，失败不会丢失事件。
     * 不先锁inbox，避免与业务的会话、所有者、绑定锁顺序倒置；不同绑定的失败互不传播。
     */
    @Transactional
    public void consume(ProjectionTargetDO target, List<SessionEvent> events) {
        if (events == null || events.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("原生事件消费批次无效");
        }
        var source = new RuntimeEventSource(target.userId(), target.bindingId(), target.sessionId(),
                RuntimeEventSource.CURRENT_MAPPING_VERSION);
        long previous = -1;
        for (SessionEvent event : events) {
            if (previous >= 0 && event.seq() != previous + 1) {
                throw new RuntimeEventConsumptionException(RuntimeEventFailureCode.EVENT_GAP, event.seq(), "原生事件消费批次不连续");
            }
            previous = event.seq();
            var received = inboxMapper.findBySequence(source, event.seq());
            if (received == null) {
                throw new RuntimeEventConsumptionException(RuntimeEventFailureCode.RECEIPT_MISSING, event.seq(), "原生日志与持久收件不一致");
            }
            if (!received.nativeEventId().equals(event.eventId())
                    || !received.sessionId().equals(source.sessionId())
                    || received.mappingVersion() != source.mappingVersion()
                    || !received.payloadDigest().equals(RuntimeEventFingerprint.of(event))) {
                throw new RuntimeEventConsumptionException(RuntimeEventFailureCode.RECEIPT_MISMATCH, event.seq(), "原生日志与持久收件不一致");
            }
            if (event.required() && !SessionEventTypes.BUILTIN.contains(event.type())) {
                throw new RuntimeEventConsumptionException(RuntimeEventFailureCode.UNSUPPORTED_REQUIRED, event.seq(), "原生必需事件类型尚不支持");
            }
            businessWriter.apply(target, event);

            var current = inboxMapper.lockBySequence(source, event.seq());
            if (current == null) {
                throw new IllegalStateException("原生事件收件已消失");
            }
            if (current.status() != RuntimeEventInboxStatus.APPLIED
                    && inboxMapper.markApplied(source, event.seq()) != 1) {
                throw new IllegalStateException("原生事件消费确认失败");
            }
        }
    }
}
