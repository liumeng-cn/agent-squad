package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.presentation.domain.ExecutionFailureRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结构化故障与有界恢复次数的持久边界。
 * <p>
 * 调用方持有会话及所有者锁，次数和排队命令同事务提交，不因进程重启或更换回合清零。
 */
@Mapper
public interface ExecutionFailureMapper {
    @ConstructorArgs({
            @Arg(column = "failure_id", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "category", javaType = String.class),
            @Arg(column = "code", javaType = String.class),
            @Arg(column = "actor", javaType = String.class),
            @Arg(column = "safe_message", javaType = String.class),
            @Arg(column = "diagnostic_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "retry_attempt", javaType = int.class)
    })
    @Select("""
            SELECT failure_id,
                   scope_id,
                   binding_id,
                   category,
                   code,
                   actor,
                   safe_message,
                   diagnostic_id,
                   status,
                   retry_attempt
            FROM sq_execution_failure
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
              AND binding_id = #{bindingId}
              AND status <> 'RESOLVED'
            ORDER BY id DESC
            LIMIT 1 FOR UPDATE
            """)
    ExecutionFailureRow latest(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("bindingId") String bindingId);

    @ConstructorArgs({
            @Arg(column = "failure_id", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "category", javaType = String.class),
            @Arg(column = "code", javaType = String.class),
            @Arg(column = "actor", javaType = String.class),
            @Arg(column = "safe_message", javaType = String.class),
            @Arg(column = "diagnostic_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "retry_attempt", javaType = int.class)
    })
    @Select("""
            SELECT f.failure_id,
                   f.scope_id,
                   f.binding_id,
                   f.category,
                   f.code,
                   f.actor,
                   f.safe_message,
                   f.diagnostic_id,
                   f.status,
                   f.retry_attempt
            FROM sq_execution_failure f
            JOIN sq_execution_scope s ON s.user_id = f.user_id
                AND s.scope_id = f.scope_id
            JOIN sq_conversation c ON c.user_id = s.user_id
                AND c.conversation_id = s.conversation_id
                AND c.generation = s.conversation_generation
            WHERE f.user_id = #{userId}
              AND s.conversation_id = #{conversationId}
              AND f.status <> 'RESOLVED'
              AND NOT EXISTS (SELECT 1 FROM sq_execution_failure newer
                  WHERE newer.user_id = f.user_id AND newer.binding_id = f.binding_id
                    AND newer.id > f.id AND newer.status <> 'RESOLVED')
              AND f.del_flag = '0'
            ORDER BY f.id
            """)
    List<ExecutionFailureRow> activeInConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Select("""
            SELECT failure_id
            FROM sq_execution_failure
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
              AND failure_key = #{key}
            FOR UPDATE
            """)
    String byKey(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("key") String key);

    @Insert("""
            INSERT INTO sq_execution_failure (
                failure_id,
                user_id,
                scope_id,
                binding_id,
                failure_key,
                category,
                code,
                actor,
                safe_message,
                diagnostic_id,
                creator_id,
                last_updater_id
            ) VALUES (
                #{row.failureId},
                #{userId},
                #{row.scopeId},
                #{row.bindingId},
                #{key},
                #{row.category},
                #{row.code},
                #{row.actor},
                #{row.safeMessage},
                #{row.diagnosticId},
                #{userId},
                #{userId}
            )
            """)
    int insert(@Param("userId") String userId, @Param("row") ExecutionFailureRow row, @Param("key") String key);

    @Update("""
            UPDATE sq_execution_failure
            SET status = #{status}
            WHERE user_id = #{userId}
              AND failure_id = #{failureId}
            """)
    int status(@Param("userId") String userId, @Param("failureId") String failureId, @Param("status") String status);

    @Update("""
            UPDATE sq_execution_failure
            SET status = 'RESOLVED'
            WHERE user_id = #{userId}
              AND binding_id = #{bindingId}
              AND status <> 'RESOLVED'
            """)
    int resolveBinding(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("""
            SELECT service_retry_attempts
            FROM sq_execution_scope
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
            FOR UPDATE
            """)
    int serviceAttempts(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Select("""
            SELECT plan_correction_attempts
            FROM sq_execution_scope
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
            FOR UPDATE
            """)
    int planAttempts(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Update("""
            UPDATE sq_execution_scope
            SET service_retry_attempts = service_retry_attempts + 1,
                retry_next_attempt_at  = #{next}
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
              AND service_retry_attempts < 2
            """)
    int reserveServiceRetry(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("next") LocalDateTime next);

    @Select("""
            SELECT retry_next_attempt_at
            FROM sq_execution_scope
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
            """)
    LocalDateTime nextAttempt(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Update("""
            UPDATE sq_execution_scope
            SET plan_correction_attempts = plan_correction_attempts + 1
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
            """)
    int invalidPlan(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Update("""
            UPDATE sq_response_execution
            SET status                   = 'RECOVERING',
                version                  = version + 1,
                next_attempt_at          = #{next}
            WHERE user_id = #{userId}
              AND binding_id = #{bindingId}
              AND cancel_requested_at IS NULL
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED')
            """)
    int responseRetrying(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("next") LocalDateTime next);

    @Select("""
            SELECT retry_of_execution_id
            FROM sq_response_execution
            WHERE user_id = #{userId}
              AND execution_id = #{executionId}
            """)
    String predecessor(@Param("userId") String userId, @Param("executionId") String executionId);

    @Select("""
            SELECT service_retry_attempts
            FROM sq_execution_scope
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
            """)
    int serviceAttemptsView(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Select("""
            SELECT CASE WHEN s.kind = 'RESPONSE' THEN r.source_message_id ELSE t.origin_message_id END
            FROM sq_execution_scope s
            LEFT JOIN sq_response_execution r ON r.user_id = s.user_id
                AND r.execution_id = s.owner_id AND s.kind = 'RESPONSE'
            LEFT JOIN sq_goal_task t ON t.user_id = s.user_id
                AND t.task_id = s.owner_id AND s.kind = 'TASK'
            WHERE s.user_id = #{userId}
              AND s.scope_id = #{scopeId}
            """)
    String sourceMessage(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Select("""
            SELECT CASE WHEN COUNT(*) = 1 THEN TRUE ELSE FALSE END
            FROM sq_response_execution r
            JOIN sq_execution_scope s ON s.user_id = r.user_id
                AND s.scope_id = r.scope_id
            JOIN sq_budget_account a ON a.user_id = s.user_id
                AND a.account_id = s.account_id
            JOIN sq_execution_failure f ON f.user_id = r.user_id
                AND f.failure_id = r.failure_id
                AND f.category = 'SERVICE_TRANSIENT'
            WHERE r.user_id = #{userId}
              AND r.execution_id = #{executionId}
              AND r.status = 'FAILED'
              AND a.status = 'OPEN'
              AND a.limit_microunits > COALESCE((SELECT SUM(CASE WHEN b.status = 'SETTLED' THEN b.settled_units ELSE b.reserved_units END)
                  FROM sq_budget_reservation b WHERE b.user_id = s.user_id AND b.account_id = s.account_id),0)
              AND NOT EXISTS (SELECT 1 FROM sq_response_execution child
                  WHERE child.user_id = r.user_id AND child.retry_of_execution_id = r.execution_id)
              AND NOT EXISTS (SELECT 1 FROM sq_operation o WHERE o.user_id = r.user_id
                  AND o.scope_id = r.scope_id AND o.status IN ('PREPARED','WAITING_APPROVAL','IN_FLIGHT','UNKNOWN','RECONCILING'))
              AND NOT EXISTS (SELECT 1 FROM sq_interaction i WHERE i.user_id = r.user_id
                  AND i.scope_id = r.scope_id AND i.status = 'OPEN')
            """)
    boolean retryAllowed(@Param("userId") String userId, @Param("executionId") String executionId);

    @Update("""
            UPDATE sq_worker_job
            SET result_candidate_json = NULL,
                candidate_guard_token = NULL,
                candidate_turn_id     = NULL
            WHERE user_id = #{userId}
              AND binding_id = #{bindingId}
              AND status = 'RUNNING'
            """)
    int clearWorkerCandidate(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_goal_task
            SET status         = 'RUNNING',
                blocked_reason = NULL,
                version        = version + 1
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND cancel_requested_at IS NULL
              AND status IN ('RUNNING','RECOVERY_BLOCKED','RECONCILING')
            """)
    int recoverTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT command_id
            FROM sq_command
            WHERE user_id = #{userId}
              AND binding_id = #{bindingId}
              AND action IN ('SUBMIT','MASTER_SIGNAL','TASK_DIRECTIVE')
              AND native_turn_id IS NOT NULL
            ORDER BY id DESC
            LIMIT 1
            """)
    String latestMasterCommand(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("""
            SELECT version
            FROM sq_response_execution
            WHERE user_id = #{userId}
              AND execution_id = #{executionId}
            """)
    Long executionVersion(@Param("userId") String userId, @Param("executionId") String executionId);

    @Update("""
            UPDATE sq_response_execution
            SET retry_of_execution_id = #{previous},
                dispatch_sequence = (SELECT c.message_seq FROM sq_conversation c
                    WHERE c.user_id = sq_response_execution.user_id AND c.conversation_id = sq_response_execution.conversation_id)
            WHERE user_id = #{userId}
              AND execution_id = #{executionId}
              AND retry_of_execution_id IS NULL
            """)
    int linkRetry(@Param("userId") String userId, @Param("executionId") String executionId, @Param("previous") String previous);

    @Select("""
            SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
            FROM sq_interaction
            WHERE user_id = #{userId}
              AND binding_id = #{bindingId}
              AND kind = #{kind}
              AND status = 'OPEN'
              AND del_flag = '0'
              AND (expires_at IS NULL OR expires_at > #{now})
            """)
    boolean userActionAvailable(@Param("userId") String userId, @Param("bindingId") String bindingId,
                                @Param("kind") String kind, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_execution_failure
            SET status        = 'RETRYING',
                retry_attempt = #{attempt}
            WHERE user_id = #{userId}
              AND failure_id = #{failureId}
            """)
    int approveRetry(@Param("userId") String userId, @Param("failureId") String failureId, @Param("attempt") int attempt);

    @Update("""
            UPDATE sq_goal_task
            SET status                     = 'FAILED',
                version                    = version + 1,
                blocked_reason             = NULL,
                completion_proposal_json   = NULL,
                proposal_binding_id        = NULL,
                proposal_turn_id           = NULL,
                proposal_tool_call_id      = NULL,
                proposal_hash              = NULL
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND status IN ('QUEUED','RUNNING')
              AND cancel_requested_at IS NULL
            """)
    int failTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_response_execution
            SET failure_id = #{failureId}
            WHERE user_id = #{userId}
              AND binding_id = #{bindingId}
            """)
    int linkFailure(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("failureId") String failureId);
}
