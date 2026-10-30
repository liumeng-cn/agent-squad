package com.agentsquad.squad.conversation.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * AgentScope 会话发现目录 SQL 边界；固定标识由后续 Bridge 使用。
 */
@Mapper
public interface BindingMapper {

    @Insert("""
            INSERT INTO sq_runtime_binding
                (binding_id, user_id, task_id, role, agent_id, framework_session_id, scope_id, status,
                 next_projection_at)
            VALUES (#{bindingId}, #{userId}, #{taskId}, 'MASTER', #{agentId}, #{sessionId}, (SELECT scope_id FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}), 'IDLE',
                    #{nextProjectionAt})
            """)
    int insertMaster(@Param("bindingId") String bindingId, @Param("userId") String userId,
                     @Param("taskId") String taskId, @Param("agentId") String agentId,
                     @Param("sessionId") String sessionId,
                     @Param("nextProjectionAt") LocalDateTime nextProjectionAt);

    @Insert("""
            INSERT INTO sq_runtime_binding
                (binding_id, user_id, task_id, role, agent_id, framework_session_id, scope_id, status,
                 next_projection_at)
            VALUES (#{bindingId}, #{userId}, #{taskId}, 'WORKER', #{agentId}, #{sessionId}, (SELECT scope_id FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}), 'IDLE',
                    #{nextProjectionAt})
            """)
    int insertWorker(@Param("bindingId") String bindingId, @Param("userId") String userId,
                     @Param("taskId") String taskId, @Param("agentId") String agentId,
                     @Param("sessionId") String sessionId,
                     @Param("nextProjectionAt") LocalDateTime nextProjectionAt);

    @Select("""
            SELECT binding_id FROM sq_runtime_binding
            WHERE user_id = #{userId} AND task_id = #{taskId} AND role = 'MASTER'
            """)
    String findMasterBindingId(@Param("userId") String userId, @Param("taskId") String taskId);

    @Insert("""
            INSERT INTO sq_runtime_binding (binding_id, user_id, scope_id, role, agent_id,
                                            framework_session_id, status, next_projection_at)
            VALUES (#{bindingId}, #{userId}, #{scopeId}, 'MASTER', #{agentId}, #{sessionId}, 'IDLE', #{nextProjectionAt})
            """)
    int insertResponse(@Param("bindingId") String bindingId, @Param("userId") String userId,
                       @Param("scopeId") String scopeId, @Param("agentId") String agentId,
                       @Param("sessionId") String sessionId, @Param("nextProjectionAt") LocalDateTime nextProjectionAt);
}
