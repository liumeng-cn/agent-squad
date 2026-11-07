package com.agentsquad.squad.integration.agentscope.inbox;

import com.agentsquad.squad.domain.entity.ProjectionTarget;
import io.agentscope.core.session.SessionEvent;

/**
 * 原生事件的业务事务写入边界
 * <p>
 * 实现必须加入消费事务，按所属业务锁序推进业务状态与连续游标；不得启动模型或在外部执行副作用。
 */
public interface RuntimeEventBusinessWriter {
    void apply(ProjectionTarget target, SessionEvent event);
}
