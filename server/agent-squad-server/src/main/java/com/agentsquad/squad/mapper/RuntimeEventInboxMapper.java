package com.agentsquad.squad.mapper;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventFailureCode;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxRow;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxStatus;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 运行集成模块的原生收件持久访问
 * <p>
 * SQL仅位于对应Mapper XML；插入不覆盖既有事件，冲突由收件服务在同一事务中裁决。
 */
@Mapper
public interface RuntimeEventInboxMapper {
    String lockBoundSession(@Param("source") RuntimeEventSource source);

    String findBoundSession(@Param("source") RuntimeEventSource source);

    String findBoundAgent(@Param("source") RuntimeEventSource source);

    List<RuntimeEventSource> pendingSources(@Param("now") LocalDateTime now);

    String lockBoundAgent(@Param("source") RuntimeEventSource source);

    int insertIfAbsent(@Param("event") RuntimeEventInboxRow event);

    List<RuntimeEventInboxRow> findCollisions(@Param("event") RuntimeEventInboxRow event);

    RuntimeEventInboxRow findBySequence(@Param("source") RuntimeEventSource source, @Param("sequence") long sequence);

    RuntimeEventInboxRow lockBySequence(@Param("source") RuntimeEventSource source, @Param("sequence") long sequence);

    int markApplied(@Param("source") RuntimeEventSource source, @Param("sequence") long sequence);

    int recordFailure(@Param("source") RuntimeEventSource source, @Param("sequence") long sequence,
                      @Param("status") RuntimeEventInboxStatus status, @Param("code") RuntimeEventFailureCode code,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
