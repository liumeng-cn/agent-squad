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
}
