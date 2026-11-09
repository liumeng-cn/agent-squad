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

    ProjectionTargetBO findTarget(@Param("userId") String userId, @Param("bindingId") String bindingId);

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
