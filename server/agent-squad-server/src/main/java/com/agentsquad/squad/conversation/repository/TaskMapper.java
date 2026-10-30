package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.TaskRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户目标 SQL 边界，不将框架执行记录伪装成任务状态。
 */
@Mapper
public interface TaskMapper {

    @Insert("""
            INSERT INTO sq_goal_task
                (task_id, user_id, conversation_id, status, input_json, event_seq,
                 budget_profile_key, budget_limit_microunits)
            VALUES
                (#{taskId}, #{userId}, #{conversationId}, 'QUEUED', #{inputJson}, 1,
                 'STANDARD', #{budgetLimitMicrounits})
            """)
    int insert(@Param("taskId") String taskId, @Param("userId") String userId,
               @Param("conversationId") String conversationId, @Param("inputJson") String inputJson,
               @Param("budgetLimitMicrounits") long budgetLimitMicrounits);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "answer_revision", javaType = long.class),
            @Arg(column = "cancel_requested_at", javaType = LocalDateTime.class),
            @Arg(column = "blocked_reason", javaType = String.class),
            @Arg(column = "create_time", javaType = LocalDateTime.class),
            @Arg(column = "last_update_time", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT id, task_id, user_id, conversation_id, status, version,
                   plan_version, answer_revision, cancel_requested_at, blocked_reason,
                   create_time, last_update_time
            FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}
            FOR UPDATE
            """)
    TaskRow lockOwnedRow(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("SELECT conversation_id FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}")
    String conversationIdOf(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("SELECT conversation_id FROM sq_conversation WHERE user_id = #{userId} AND conversation_id = #{conversationId} FOR UPDATE")
    String lockConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);

    /**
     * 所有目标写入遵循会话→目标锁序，避免与按Scope预留预算的事务反向取锁。
     */
    default TaskRow lockOwned(String userId, String taskId) {
        String conversationId = conversationIdOf(userId, taskId);
        if (conversationId == null) {
            return null;
        }
        lockConversation(userId, conversationId);
        return lockOwnedRow(userId, taskId);
    }

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "answer_revision", javaType = long.class),
            @Arg(column = "cancel_requested_at", javaType = LocalDateTime.class),
            @Arg(column = "blocked_reason", javaType = String.class),
            @Arg(column = "create_time", javaType = LocalDateTime.class),
            @Arg(column = "last_update_time", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT id, task_id, user_id, conversation_id, status, version,
                   plan_version, answer_revision, cancel_requested_at, blocked_reason,
                   create_time, last_update_time
            FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}
            """)
    TaskRow findOwned(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT input_json FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}
            """)
    String findInputJson(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'CANCELLING', version = version + 1, cancel_requested_at = #{cancelledAt}
            WHERE user_id = #{userId} AND task_id = #{taskId} AND version = #{expectedVersion}
              AND status NOT IN ('CANCELLING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')
            """)
    int updateCancelling(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("cancelledAt") LocalDateTime cancelledAt);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'CANCELLED', version = version + 1, cancel_requested_at = #{cancelledAt}
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND status = 'QUEUED'
            """)
    int updateCancelled(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("cancelledAt") LocalDateTime cancelledAt);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'QUEUED', blocked_reason = NULL, version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId} AND version = #{expectedVersion}
            """)
    int updateResuming(@Param("userId") String userId, @Param("taskId") String taskId,
                       @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'RECOVERY_BLOCKED', blocked_reason = #{reason}, version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId} AND status = 'QUEUED'
              AND cancel_requested_at IS NULL
            """)
    int blockRejectedResume(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("reason") String reason);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "answer_revision", javaType = long.class),
            @Arg(column = "cancel_requested_at", javaType = LocalDateTime.class),
            @Arg(column = "blocked_reason", javaType = String.class),
            @Arg(column = "create_time", javaType = LocalDateTime.class),
            @Arg(column = "last_update_time", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT id, task_id, user_id, conversation_id, status, version,
                   plan_version, answer_revision, cancel_requested_at, blocked_reason,
                   create_time, last_update_time
            FROM sq_goal_task
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND (#{cursorId} IS NULL OR id < #{cursorId})
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<TaskRow> listByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                     @Param("cursorId") Long cursorId, @Param("limit") int limit);
}
