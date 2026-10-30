package com.agentsquad.squad.runtime.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 执行资料失效后的持久阻塞写入。
 * <p>
 * 不清理未知外部操作和预算；仅阻止旧上下文继续派发与发布正文。
 */
@Mapper
public interface ExecutionRestrictionMapper {
    @Update("""
            UPDATE sq_response_execution
            SET status = 'FAILED', version = version + 1,
                completion_proposal_json = NULL, proposal_hash = NULL
            WHERE user_id = #{userId} AND scope_id = #{scopeId}
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED')
              AND cancel_requested_at IS NULL
            """)
    int failResponse(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'RECOVERY_BLOCKED', version = version + 1,
                blocked_reason = '执行资料或技能授权已变化', completion_proposal_json = NULL,
                proposal_hash = NULL
            WHERE user_id = #{userId} AND scope_id = #{scopeId}
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED','RECOVERY_BLOCKED')
              AND cancel_requested_at IS NULL
            """)
    int blockTask(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Update("""
            UPDATE sq_worker_job
            SET status = 'RECOVERY_BLOCKED', guard_token = NULL,
                guard_lease_until = NULL, version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND status IN ('READY','RUNNING','BLOCKED','WAITING_INPUT','WAITING_APPROVAL')
            """)
    int blockJobs(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_command
            SET status = 'BLOCKED', delivery_token = NULL, delivery_lease_until = NULL,
                last_error_code = 'EXECUTION_CONTEXT_INVALIDATED'
            WHERE user_id = #{userId} AND scope_id = #{scopeId}
              AND status IN ('PENDING','DELIVERING') AND action <> 'STOP'
            """)
    int blockCommands(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
