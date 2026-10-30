package com.agentsquad.squad.sandbox.repository;

import com.agentsquad.squad.sandbox.domain.SandboxBindingRow;
import com.agentsquad.squad.sandbox.domain.SandboxRunRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 沙箱执行与固定会话身份的持久边界。
 * <p>
 * 原生调用和业务键分别去重；运行失联不自动回到待执行状态。
 */
@Mapper
public interface SandboxMapper {
    @Select("""
            SELECT user_id, scope_id, binding_id, framework_session_id AS session_id, role
            FROM sq_runtime_binding
            WHERE user_id = #{userId} AND framework_session_id = #{sessionId} AND del_flag = '0'
            """)
    SandboxBindingRow binding(@Param("userId") String userId, @Param("sessionId") String sessionId);

    @Select("""
            SELECT COUNT(*) FROM sq_worker_job j
            JOIN sq_plan p ON p.task_id = j.task_id AND p.plan_version = j.plan_version
            WHERE j.user_id = #{userId} AND j.binding_id = #{bindingId}
              AND j.guard_token = #{guardToken} AND j.guard_lease_until > CURRENT_TIMESTAMP
              AND j.status = 'RUNNING' AND j.cancel_requested_at IS NULL AND p.status = 'ACTIVE'
            """)
    int activeWorker(@Param("userId") String userId, @Param("bindingId") String bindingId,
                     @Param("guardToken") String guardToken);

    @Select("""
            SELECT execution_id, user_id, scope_id, binding_id, request_hash, status, result_json
            FROM sq_sandbox_execution
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND (logical_key = #{logicalKey} OR (turn_id = #{turnId} AND tool_call_id = #{toolCallId}))
            FOR UPDATE
            """)
    SandboxRunRow prior(@Param("userId") String userId, @Param("bindingId") String bindingId,
                        @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
                        @Param("toolCallId") String toolCallId);

    @Select("""
            SELECT execution_id, user_id, scope_id, binding_id, request_hash, status, result_json
            FROM sq_sandbox_execution WHERE user_id = #{userId} AND execution_id = #{executionId} FOR UPDATE
            """)
    SandboxRunRow find(@Param("userId") String userId, @Param("executionId") String executionId);

    @Insert("""
            INSERT INTO sq_sandbox_execution
                (execution_id, user_id, scope_id, binding_id, logical_key, turn_id, tool_call_id,
                 request_hash, status, deadline_at, creator_id, last_updater_id)
            VALUES
                (#{executionId}, #{userId}, #{scopeId}, #{bindingId}, #{logicalKey}, #{turnId}, #{toolCallId},
                 #{requestHash}, 'IN_FLIGHT', #{deadlineAt}, #{userId}, #{userId})
            """)
    int insert(@Param("executionId") String executionId, @Param("userId") String userId,
               @Param("scopeId") String scopeId, @Param("bindingId") String bindingId,
               @Param("logicalKey") String logicalKey, @Param("turnId") String turnId,
               @Param("toolCallId") String toolCallId, @Param("requestHash") String requestHash,
               @Param("deadlineAt") java.time.LocalDateTime deadlineAt);

    @Update("""
            UPDATE sq_sandbox_execution SET status = #{status}, result_json = #{resultJson},
                last_update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND execution_id = #{executionId} AND status = 'IN_FLIGHT'
            """)
    int finish(@Param("userId") String userId, @Param("executionId") String executionId,
               @Param("status") String status, @Param("resultJson") String resultJson);

    @Select("""
            SELECT execution_id, user_id, scope_id, binding_id, request_hash, status, result_json
            FROM sq_sandbox_execution
            WHERE status = 'IN_FLIGHT' AND deadline_at < CURRENT_TIMESTAMP
            ORDER BY id LIMIT 100
            """)
    java.util.List<SandboxRunRow> expired();

    @Update("""
            UPDATE sq_sandbox_execution SET status = 'UNKNOWN', last_update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND execution_id = #{executionId}
              AND status = 'IN_FLIGHT' AND deadline_at < CURRENT_TIMESTAMP
            """)
    int expireOne(@Param("userId") String userId, @Param("executionId") String executionId);
}
