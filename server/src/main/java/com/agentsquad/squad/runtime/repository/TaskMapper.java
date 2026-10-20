package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.TaskRow;
import org.apache.ibatis.annotations.*;

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
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("""
            SELECT task_id, user_id, conversation_id, status, version
            FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}
            """)
    TaskRow findOwned(@Param("userId") String userId, @Param("taskId") String taskId);
}
