package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.TaskRow;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户目标 SQL 边界，不将框架执行记录伪装成任务状态。
 */
@Mapper
public interface TaskMapper {

    @Insert("""
            INSERT INTO sq_goal_task (task_id, user_id, conversation_id, status, input_json)
            VALUES (#{taskId}, #{userId}, #{conversationId}, 'QUEUED', #{inputJson})
            """)
    int insert(@Param("taskId") String taskId, @Param("userId") String userId,
               @Param("conversationId") String conversationId, @Param("inputJson") String inputJson);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "create_time", javaType = LocalDateTime.class),
            @Arg(column = "last_update_time", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT id, task_id, user_id, conversation_id, status, version, create_time, last_update_time
            FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}
            FOR UPDATE
            """)
    TaskRow lockOwned(@Param("userId") String userId, @Param("taskId") String taskId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "create_time", javaType = LocalDateTime.class),
            @Arg(column = "last_update_time", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT id, task_id, user_id, conversation_id, status, version, create_time, last_update_time
            FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}
            """)
    TaskRow findOwned(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'CANCELLING', version = version + 1, cancel_requested_at = #{cancelledAt}
            WHERE user_id = #{userId} AND task_id = #{taskId} AND version = #{expectedVersion}
            """)
    int updateCancelling(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("cancelledAt") LocalDateTime cancelledAt);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'QUEUED', version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId} AND version = #{expectedVersion}
            """)
    int updateResuming(@Param("userId") String userId, @Param("taskId") String taskId,
                       @Param("expectedVersion") long expectedVersion);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "create_time", javaType = LocalDateTime.class),
            @Arg(column = "last_update_time", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT id, task_id, user_id, conversation_id, status, version, create_time, last_update_time
            FROM sq_goal_task
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND (#{cursorId} IS NULL OR id < #{cursorId})
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<TaskRow> listByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                     @Param("cursorId") Long cursorId, @Param("limit") int limit);
}
