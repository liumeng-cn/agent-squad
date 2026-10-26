package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 原生会话投影水位与任务进度的数据库访问边界。
 */
@Mapper
public interface ProjectionMapper {

    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "agent_id", javaType = String.class),
            @Arg(column = "framework_session_id", javaType = String.class),
            @Arg(column = "native_event_cursor", javaType = long.class)
    })
    @Select("""
            SELECT b.user_id, b.task_id, b.binding_id, b.agent_id,
                   b.framework_session_id, b.native_event_cursor
            FROM sq_runtime_binding b
            JOIN sq_goal_task t ON t.user_id = b.user_id AND t.task_id = b.task_id
            WHERE b.next_projection_at <= #{now}
              AND t.status NOT IN ('SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED', 'RECOVERY_BLOCKED')
              AND EXISTS (SELECT 1 FROM sq_command c
                          WHERE c.user_id = b.user_id AND c.binding_id = b.binding_id
                            AND c.status IN ('DELIVERED', 'APPLIED'))
              AND (b.role = 'MASTER' OR EXISTS (
                  SELECT 1 FROM sq_worker_job j
                  WHERE j.user_id = b.user_id AND j.binding_id = b.binding_id
                    AND j.status IN ('READY', 'RUNNING', 'WAITING_INPUT', 'WAITING_APPROVAL',
                                     'RECONCILING', 'CANCELLING')))
            ORDER BY b.next_projection_at, b.id LIMIT 20
            """)
    List<ProjectionTarget> findDueTargets(@Param("now") LocalDateTime now);

    @Select("""
            SELECT native_event_cursor FROM sq_runtime_binding
            WHERE user_id = #{userId} AND binding_id = #{bindingId} FOR UPDATE
            """)
    Long lockCursor(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_runtime_binding SET native_event_cursor = #{next}
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND native_event_cursor = #{previous}
            """)
    int advanceCursor(@Param("userId") String userId, @Param("bindingId") String bindingId,
                      @Param("previous") long previous, @Param("next") long next);

    @Update("""
            UPDATE sq_runtime_binding SET next_projection_at = #{nextPollAt}
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
            """)
    int defer(@Param("userId") String userId, @Param("bindingId") String bindingId,
              @Param("nextPollAt") LocalDateTime nextPollAt);

    @Update("""
            UPDATE sq_goal_task SET event_seq = event_seq + 1
            WHERE user_id = #{userId} AND task_id = #{taskId}
            """)
    int reserveEventSequence(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("SELECT event_seq FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}")
    Long eventSequence(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_goal_task SET status = 'RUNNING', version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId} AND status = 'QUEUED'
            """)
    int markRunning(@Param("userId") String userId, @Param("taskId") String taskId);
}
