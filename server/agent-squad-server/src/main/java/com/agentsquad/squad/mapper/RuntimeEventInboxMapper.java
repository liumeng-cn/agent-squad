package com.agentsquad.squad.mapper;

import com.agentsquad.squad.enums.RuntimeEventFailureCode;
import com.agentsquad.squad.po.RuntimeEventInboxPO;
import com.agentsquad.squad.enums.RuntimeEventInboxStatus;
import com.agentsquad.squad.bo.RuntimeEventSourceBO;
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
    String lockBoundSession(@Param("source") RuntimeEventSourceBO source);

    String findBoundSession(@Param("source") RuntimeEventSourceBO source);

    String findBoundAgent(@Param("source") RuntimeEventSourceBO source);

    List<RuntimeEventSourceBO> pendingSources(@Param("now") LocalDateTime now);

    String lockBoundAgent(@Param("source") RuntimeEventSourceBO source);

    int insertIfAbsent(@Param("event") RuntimeEventInboxPO event);

    List<RuntimeEventInboxPO> findCollisions(@Param("event") RuntimeEventInboxPO event);

    RuntimeEventInboxPO findBySequence(@Param("source") RuntimeEventSourceBO source, @Param("sequence") long sequence);

    RuntimeEventInboxPO lockBySequence(@Param("source") RuntimeEventSourceBO source, @Param("sequence") long sequence);

    int markApplied(@Param("source") RuntimeEventSourceBO source, @Param("sequence") long sequence);

    int recordFailure(@Param("source") RuntimeEventSourceBO source, @Param("sequence") long sequence,
                      @Param("status") RuntimeEventInboxStatus status, @Param("code") RuntimeEventFailureCode code,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
