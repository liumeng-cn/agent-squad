package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.WorkerLaunchRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Worker Binding、业务 guard 和结果候选的数据库访问边界。
 */
@Mapper
public interface WorkerDispatchMapper {

    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "node_key", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "job_status", javaType = String.class),
            @Arg(column = "plan_status", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "agent_id", javaType = String.class),
            @Arg(column = "input_json", javaType = String.class),
            @Arg(column = "cancel_requested_at", javaType = LocalDateTime.class),
            @Arg(column = "guard_token", javaType = String.class),
            @Arg(column = "guard_lease_until", javaType = LocalDateTime.class),
            @Arg(column = "result_candidate_json", javaType = String.class),
            @Arg(column = "candidate_guard_token", javaType = String.class),
            @Arg(column = "candidate_turn_id", javaType = String.class)
    })
    @Select("""
            SELECT j.user_id, j.task_id, j.job_id, j.plan_version, j.node_key,
                   j.worker_key, j.worker_version, j.status AS job_status,
                   p.status AS plan_status, j.binding_id, b.agent_id, j.input_json,
                   j.cancel_requested_at, j.guard_token, j.guard_lease_until,
                   j.result_candidate_json, j.candidate_guard_token, j.candidate_turn_id
            FROM sq_worker_job j
            JOIN sq_plan p ON p.task_id = j.task_id AND p.plan_version = j.plan_version
            JOIN sq_runtime_binding b ON b.user_id = j.user_id AND b.binding_id = j.binding_id
            WHERE j.user_id = #{userId} AND j.binding_id = #{bindingId} AND b.role = 'WORKER'
            """)
    WorkerLaunchRow findByBinding(@Param("userId") String userId,
                                  @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_worker_job j
            SET j.status = 'RUNNING', j.guard_token = #{guardToken},
                j.candidate_guard_token = CASE WHEN j.result_candidate_json IS NOT NULL
                    THEN #{guardToken} ELSE j.candidate_guard_token END,
                j.guard_lease_until = #{leaseUntil}, j.version = j.version + 1
            WHERE j.user_id = #{userId} AND j.binding_id = #{bindingId}
              AND j.cancel_requested_at IS NULL
              AND (j.status = 'READY'
                   OR (j.status = 'RUNNING' AND j.guard_lease_until < #{now}))
              AND EXISTS (SELECT 1 FROM sq_plan p
                          WHERE p.task_id = j.task_id AND p.plan_version = j.plan_version
                            AND p.status = 'ACTIVE')
            """)
    int claimGuard(@Param("userId") String userId, @Param("bindingId") String bindingId,
                   @Param("guardToken") String guardToken, @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE sq_worker_job SET guard_lease_until = #{leaseUntil}
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND status = 'RUNNING' AND guard_token = #{guardToken}
              AND cancel_requested_at IS NULL AND guard_lease_until > #{now}
            """)
    int renewGuard(@Param("userId") String userId, @Param("bindingId") String bindingId,
                   @Param("guardToken") String guardToken, @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE sq_worker_job SET result_candidate_json = #{resultJson},
                candidate_guard_token = #{guardToken}, candidate_turn_id = #{turnId},
                version = version + 1
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND status = 'RUNNING' AND guard_token = #{guardToken}
              AND guard_lease_until > #{now} AND cancel_requested_at IS NULL
              AND result_candidate_json IS NULL
              AND EXISTS (SELECT 1 FROM sq_plan p WHERE p.task_id = sq_worker_job.task_id
                          AND p.plan_version = sq_worker_job.plan_version AND p.status = 'ACTIVE')
            """)
    int saveCandidate(@Param("userId") String userId, @Param("bindingId") String bindingId,
                      @Param("guardToken") String guardToken, @Param("turnId") String turnId,
                      @Param("resultJson") String resultJson, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_worker_job SET status = 'SUCCEEDED', result_json = result_candidate_json,
                guard_token = NULL, guard_lease_until = NULL, version = version + 1
            WHERE user_id = #{userId} AND binding_id = #{bindingId} AND status = 'RUNNING'
              AND candidate_turn_id = #{turnId} AND candidate_guard_token = guard_token
              AND guard_lease_until > #{now}
              AND cancel_requested_at IS NULL
              AND EXISTS (SELECT 1 FROM sq_plan p WHERE p.task_id = sq_worker_job.task_id
                          AND p.plan_version = sq_worker_job.plan_version AND p.status = 'ACTIVE')
            """)
    int finalizeSuccess(@Param("userId") String userId, @Param("bindingId") String bindingId,
                        @Param("turnId") String turnId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_worker_job SET status = 'SUPERSEDED', guard_token = NULL,
                guard_lease_until = NULL, version = version + 1
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND status IN ('RUNNING', 'WAITING_INPUT', 'WAITING_APPROVAL',
                             'RECONCILING', 'CANCELLING')
              AND cancel_requested_at IS NOT NULL
              AND EXISTS (SELECT 1 FROM sq_plan p WHERE p.task_id = sq_worker_job.task_id
                          AND p.plan_version = sq_worker_job.plan_version
                          AND p.status = 'SUPERSEDED')
            """)
    int supersedeInFlight(@Param("userId") String userId,
                          @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_worker_job SET status = 'FAILED', guard_token = NULL,
                guard_lease_until = NULL, version = version + 1
            WHERE user_id = #{userId} AND binding_id = #{bindingId} AND status = 'RUNNING'
            """)
    int markFailed(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_worker_job SET status = 'RECOVERY_BLOCKED', guard_token = NULL,
                guard_lease_until = NULL, version = version + 1
            WHERE user_id = #{userId} AND binding_id = #{bindingId} AND status = 'RUNNING'
            """)
    int markRecoveryBlocked(@Param("userId") String userId,
                            @Param("bindingId") String bindingId);
}
