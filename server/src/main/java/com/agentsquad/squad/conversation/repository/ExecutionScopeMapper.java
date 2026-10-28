package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
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
                      AND t.scope_id = s.scope_id AND t.del_flag = '0'))
                OR (s.kind = 'RESPONSE' AND EXISTS (SELECT 1 FROM sq_response_execution r
                    WHERE r.user_id = s.user_id AND r.execution_id = s.owner_id
                      AND r.scope_id = s.scope_id AND r.del_flag = '0')))
            """)
    ExecutionScopeRow findActiveOwned(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
