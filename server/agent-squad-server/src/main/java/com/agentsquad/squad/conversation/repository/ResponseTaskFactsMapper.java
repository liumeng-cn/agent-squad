package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ResponseJobFact;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话当前目标的受权进度查询边界。
 * <p>
 * 调用方持有会话与目标锁；当前读只取当前计划，不拼接旧计划结果或Worker原生日志。
 */
@Mapper
public interface ResponseTaskFactsMapper {
    @Select("""
            SELECT s.conversation_generation
            FROM sq_goal_task t
            JOIN sq_execution_scope s
              ON s.user_id = t.user_id
             AND s.scope_id = t.scope_id
             AND s.owner_id = t.task_id
             AND s.conversation_id = t.conversation_id
             AND s.kind = 'TASK'
            WHERE t.user_id = #{userId}
              AND t.task_id = #{taskId}
              AND t.del_flag = '0'
              AND s.del_flag = '0'
            FOR UPDATE
            """)
    Long scopeGeneration(@Param("userId") String userId, @Param("taskId") String taskId);

    @ConstructorArgs({
            @Arg(column = "node_key", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    @Select("""
            SELECT node_key,
                   status
            FROM sq_worker_job
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND plan_version = #{planVersion}
              AND del_flag = '0'
            ORDER BY id
            FOR UPDATE
            """)
    List<ResponseJobFact> jobs(@Param("userId") String userId, @Param("taskId") String taskId,
                               @Param("planVersion") long planVersion);

    @ConstructorArgs({
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "goal_revision", javaType = long.class)
    })
    @Select("""
            SELECT t.task_id,
                   t.origin_message_id AS source_message_id,
                   t.status,
                   t.version,
                   t.goal_revision
            FROM sq_goal_task t
            JOIN sq_execution_scope s ON s.user_id = t.user_id
                AND s.scope_id = t.scope_id
            JOIN sq_message m ON m.user_id = t.user_id
                AND m.message_id = t.origin_message_id
            WHERE t.user_id = #{userId}
              AND t.conversation_id = #{conversationId}
              AND s.conversation_generation = #{generation}
              AND m.message_seq <= #{through}
              AND t.del_flag = '0'
            ORDER BY t.id DESC
            LIMIT 5 FOR UPDATE
            """)
    List<com.agentsquad.squad.conversation.domain.ResponseTaskStatusFact> recentTasks(@Param("userId") String userId,
            @Param("conversationId") String conversationId,@Param("generation") long generation,@Param("through") long through);

    @ConstructorArgs({
            @Arg(column = "category", javaType = String.class),
            @Arg(column = "actor", javaType = String.class),
            @Arg(column = "safe_message", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class)
    })
    @Select("""
            SELECT f.category,
                   f.actor,
                   f.safe_message,
                   m.message_id AS source_message_id,
                   CASE WHEN s.kind = 'TASK' THEN s.owner_id ELSE NULL END AS task_id
            FROM sq_execution_failure f
            JOIN sq_execution_scope s ON s.user_id = f.user_id
                AND s.scope_id = f.scope_id
            LEFT JOIN sq_response_execution r ON r.user_id = s.user_id
                AND r.execution_id = s.owner_id AND s.kind = 'RESPONSE'
            LEFT JOIN sq_goal_task t ON t.user_id = s.user_id
                AND t.task_id = s.owner_id AND s.kind = 'TASK'
            JOIN sq_message m ON m.user_id = s.user_id
                AND m.message_id = COALESCE(r.source_message_id, t.origin_message_id)
            WHERE f.user_id = #{userId}
              AND s.conversation_id = #{conversationId}
              AND s.conversation_generation = #{generation}
              AND m.message_seq <= #{through}
              AND f.status <> 'RESOLVED'
              AND f.del_flag = '0'
            ORDER BY f.id DESC
            LIMIT 10 FOR UPDATE
            """)
    List<com.agentsquad.squad.conversation.domain.ResponseFailureFact> recentFailures(@Param("userId") String userId,
            @Param("conversationId") String conversationId,@Param("generation") long generation,@Param("through") long through);
}
