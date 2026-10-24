package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.ConversationRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

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
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "active_task_id", javaType = String.class),
            @Arg(column = "generation", javaType = long.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("""
            SELECT id, conversation_id, user_id, title, status, active_task_id, generation, version
            FROM sq_conversation WHERE user_id = #{userId} AND conversation_id = #{conversationId}
            FOR UPDATE
            """)
    ConversationRow lockOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "active_task_id", javaType = String.class),
            @Arg(column = "generation", javaType = long.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("""
            SELECT id, conversation_id, user_id, title, status, active_task_id, generation, version
            FROM sq_conversation WHERE user_id = #{userId} AND conversation_id = #{conversationId}
            """)
    ConversationRow findOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "active_task_id", javaType = String.class),
            @Arg(column = "generation", javaType = long.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("""
            SELECT id, conversation_id, user_id, title, status, active_task_id, generation, version
            FROM sq_conversation WHERE user_id = #{userId}
              AND (#{cursorId} IS NULL OR id < #{cursorId})
              AND status = 'ACTIVE'
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<ConversationRow> listByOwner(@Param("userId") String userId, @Param("cursorId") Long cursorId,
                                      @Param("limit") int limit);

    @Update("""
            UPDATE sq_conversation SET active_task_id = #{taskId}, version = version + 1
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND status = 'ACTIVE' AND active_task_id IS NULL
            """)
    int activateTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_conversation SET active_task_id = NULL, version = version + 1
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND active_task_id = #{taskId}
            """)
    int clearActiveTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                        @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_conversation
            SET status = 'DELETING', generation = generation + 1, version = version + 1
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND status = 'ACTIVE' AND version = #{expectedVersion}
            """)
    int markDeleting(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE sq_conversation SET status = 'DELETED', version = version + 1
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND status = 'DELETING'
            """)
    int markDeleted(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "active_task_id", javaType = String.class),
            @Arg(column = "generation", javaType = long.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("""
            SELECT id, conversation_id, user_id, title, status, active_task_id, generation, version
            FROM sq_conversation
            WHERE status = 'DELETING' AND active_task_id IS NULL
            ORDER BY id LIMIT #{limit}
            """)
    List<ConversationRow> findDeletable(@Param("limit") int limit);
}
