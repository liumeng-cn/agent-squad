package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.ConversationRow;
import org.apache.ibatis.annotations.*;

/**
 * 对话 SQL 边界；任务受理时用行锁串行化同一会话。
 */
@Mapper
public interface ConversationMapper {

    @Insert("""
            INSERT INTO sq_conversation (conversation_id, user_id, title, status)
            VALUES (#{conversationId}, #{userId}, #{title}, 'ACTIVE')
            """)
    int insert(@Param("conversationId") String conversationId, @Param("userId") String userId,
               @Param("title") String title);

    @ConstructorArgs({
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "active_task_id", javaType = String.class)
    })
    @Select("""
            SELECT conversation_id, user_id, title, status, active_task_id
            FROM sq_conversation WHERE user_id = #{userId} AND conversation_id = #{conversationId}
            FOR UPDATE
            """)
    ConversationRow lockOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Update("""
            UPDATE sq_conversation SET active_task_id = #{taskId}, version = version + 1
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND status = 'ACTIVE' AND active_task_id IS NULL
            """)
    int activateTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("taskId") String taskId);
}
