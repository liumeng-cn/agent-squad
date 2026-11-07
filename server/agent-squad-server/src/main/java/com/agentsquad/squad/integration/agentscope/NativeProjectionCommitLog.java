package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * 原生投影提交后的结果日志
 * <p>
 * 整批业务事务提交后才记录成功，避免后续事件失败回滚时，日志却提前宣称状态已经更新。
 */
@Slf4j
public record NativeProjectionCommitLog(ProjectionTargetDO target,
                                        NativeEventView event) implements TransactionSynchronization {
    @Override
    public void afterCommit() {
        log.info("Agent执行事件已更新到业务状态 taskId={} bindingId={} sourceSeq={} nativeEventId={} type={} turnId={} outcome={}",
                target.taskId(), target.bindingId(), event.sequence(), event.eventId(), event.type(), event.turnId(), event.outcome());
    }
}
