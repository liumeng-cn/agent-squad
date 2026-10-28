package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.domain.ExecutionOwnerRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 执行归属查询边界。
 * <p>
 * 必须连同会话归属、代际和删除状态读取，不能仅凭客户端提供的 scopeId 获得执行权限。
 */
@Mapper
public interface ExecutionScopeMapper {

    @ConstructorArgs({
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "owner_id", javaType = String.class),
            @Arg(column = "account_id", javaType = String.class)
    })
    @Select("""
            SELECT s.scope_id, s.user_id, s.conversation_id, s.conversation_generation,
                   s.kind, s.owner_id, s.account_id
            FROM sq_execution_scope s JOIN sq_conversation c
                ON c.user_id = s.user_id AND c.conversation_id = s.conversation_id
            WHERE s.user_id = #{userId} AND s.scope_id = #{scopeId}
              AND s.del_flag = '0' AND c.del_flag = '0' AND c.status = 'ACTIVE'
              AND s.conversation_generation = c.generation
              AND ((s.kind = 'TASK' AND EXISTS (SELECT 1 FROM sq_goal_task t
                    WHERE t.user_id = s.user_id AND t.task_id = s.owner_id
                      AND t.scope_id = s.scope_id AND t.conversation_id = s.conversation_id AND t.del_flag = '0'))
                OR (s.kind = 'RESPONSE' AND EXISTS (SELECT 1 FROM sq_response_execution r
                    WHERE r.user_id = s.user_id AND r.execution_id = s.owner_id
                      AND r.scope_id = s.scope_id AND r.conversation_id = s.conversation_id AND r.del_flag = '0')))
            """)
    ExecutionScopeRow findActiveOwned(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Insert("""
            INSERT INTO sq_budget_account (account_id, user_id, limit_microunits, profile_key, status)
            VALUES (#{accountId}, #{userId}, #{limit}, #{profileKey}, 'OPEN')
            """)
    int insertAccount(@Param("userId") String userId, @Param("accountId") String accountId,
                      @Param("profileKey") String profileKey, @Param("limit") long limit);

    @Insert("""
            INSERT INTO sq_execution_scope (scope_id, user_id, conversation_id, conversation_generation,
                                            kind, owner_id, account_id)
            VALUES (#{scopeId}, #{userId}, #{conversationId}, #{generation}, #{kind}, #{ownerId}, #{accountId})
            """)
    int insertScope(@Param("scopeId") String scopeId, @Param("userId") String userId,
                    @Param("conversationId") String conversationId, @Param("generation") long generation,
                    @Param("kind") String kind, @Param("ownerId") String ownerId, @Param("accountId") String accountId);

    @Update("""
            UPDATE sq_goal_task SET scope_id = #{scopeId}
            WHERE user_id = #{userId} AND task_id = #{taskId} AND scope_id IS NULL
              AND EXISTS (SELECT 1 FROM sq_execution_scope s
                  WHERE s.user_id = sq_goal_task.user_id AND s.scope_id = #{scopeId}
                    AND s.owner_id = sq_goal_task.task_id AND s.kind = 'TASK'
                    AND s.conversation_id = sq_goal_task.conversation_id)
            """)
    int linkTaskScope(@Param("userId") String userId, @Param("taskId") String taskId, @Param("scopeId") String scopeId);

    @ConstructorArgs({
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "owner_id", javaType = String.class),
            @Arg(column = "account_id", javaType = String.class)
    })
    @Select("""
            SELECT scope_id, user_id, conversation_id, conversation_generation, kind, owner_id, account_id
            FROM sq_execution_scope WHERE user_id = #{userId} AND scope_id = #{scopeId} AND del_flag = '0'
            """)
    ExecutionScopeRow findOwned(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Select("SELECT binding_id FROM sq_runtime_binding WHERE user_id = #{userId} AND framework_session_id = #{sessionId} AND del_flag = '0'")
    String findBindingBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    @Select("SELECT scope_id FROM sq_runtime_binding WHERE user_id = #{userId} AND framework_session_id = #{sessionId} AND del_flag = '0'")
    String findScopeBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    @Select("SELECT scope_id FROM sq_runtime_binding WHERE user_id = #{userId} AND binding_id = #{bindingId} AND del_flag = '0'")
    String findScopeByBinding(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("SELECT scope_id FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}")
    String findScopeByTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @ConstructorArgs({
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "cancellation_requested", javaType = boolean.class),
            @Arg(column = "del_flag", javaType = String.class)
    })
    @Select("""
            SELECT status, (cancel_requested_at IS NOT NULL) AS cancellation_requested, del_flag
            FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{ownerId} AND scope_id = #{scopeId} FOR UPDATE
            """)
    ExecutionOwnerRow lockTaskOwner(@Param("userId") String userId, @Param("ownerId") String ownerId,
                                   @Param("scopeId") String scopeId);

    @ConstructorArgs({
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "cancellation_requested", javaType = boolean.class),
            @Arg(column = "del_flag", javaType = String.class)
    })
    @Select("""
            SELECT status, (cancel_requested_at IS NOT NULL) AS cancellation_requested, del_flag
            FROM sq_response_execution WHERE user_id = #{userId} AND execution_id = #{ownerId} AND scope_id = #{scopeId} FOR UPDATE
            """)
    ExecutionOwnerRow lockResponseOwner(@Param("userId") String userId, @Param("ownerId") String ownerId,
                                       @Param("scopeId") String scopeId);

    @Update("""
            UPDATE sq_goal_task SET status = 'QUEUED', version = version + 1
            WHERE user_id = #{userId} AND scope_id = #{scopeId}
              AND status IN ('WAITING_INPUT', 'WAITING_APPROVAL') AND cancel_requested_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM sq_interaction i WHERE i.user_id = #{userId}
                  AND i.scope_id = #{scopeId} AND i.status = 'OPEN' AND i.del_flag = '0')
            """)
    int queueTaskAfterInteraction(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Update("""
            UPDATE sq_response_execution SET status = 'QUEUED', version = version + 1
            WHERE user_id = #{userId} AND scope_id = #{scopeId}
              AND status IN ('WAITING_INPUT', 'WAITING_APPROVAL') AND cancel_requested_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM sq_interaction i WHERE i.user_id = #{userId}
                  AND i.scope_id = #{scopeId} AND i.status = 'OPEN' AND i.del_flag = '0')
            """)
    int queueResponseAfterInteraction(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
