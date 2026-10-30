package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 原生会话投影水位与任务进度的数据库访问边界。
 */
@Mapper
public interface ProjectionMapper {

    List<ProjectionTarget> findDueTargets(@Param("now") LocalDateTime now);

    Long lockCursor(@Param("userId") String userId, @Param("bindingId") String bindingId);

    int advanceCursor(@Param("userId") String userId, @Param("bindingId") String bindingId,
                      @Param("previous") long previous, @Param("next") long next);

    int defer(@Param("userId") String userId, @Param("bindingId") String bindingId,
              @Param("nextPollAt") LocalDateTime nextPollAt);

    int reserveEventSequence(@Param("userId") String userId, @Param("taskId") String taskId);

    Long eventSequence(@Param("userId") String userId, @Param("taskId") String taskId);

    int markRunning(@Param("userId") String userId, @Param("taskId") String taskId);

    int recordEndedTurn(@Param("userId") String userId, @Param("bindingId") String bindingId,
                        @Param("turnId") String turnId);

    int settleControls(@Param("userId") String userId, @Param("bindingId") String bindingId);
}
