package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.ProjectionTargetBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 原生会话投影水位与任务进度的
 */
@Mapper
public interface ProjectionMapper {

    List<ProjectionTargetBO> findDueTargets(@Param("now") LocalDateTime now);

    List<ProjectionTargetBO> findReconciliationTargets(@Param("now") LocalDateTime now);

    ProjectionTargetBO findTarget(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    Long lockCursor(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int advanceCursor(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                      @Param("previous") long previous, @Param("next") long next);

    int defer(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
              @Param("nextPollAt") LocalDateTime nextPollAt);

    int reserveEventSequence(@Param("userId") Long userId, @Param("taskId") Long taskId);

    Long eventSequence(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int markRunning(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int recordEndedTurn(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                        @Param("turnId") String turnId);

    int settleControls(@Param("userId") Long userId, @Param("bindingId") Long bindingId);
}
