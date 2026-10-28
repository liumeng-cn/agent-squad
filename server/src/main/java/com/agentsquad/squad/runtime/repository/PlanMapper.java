package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.PlanContextRow;
import com.agentsquad.squad.runtime.domain.PlanRow;
import com.agentsquad.squad.runtime.domain.WorkerJobRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 协作计划与委派节点的 SQL 边界。
 * <p>
 * 计划头与全部节点在同一平台事务内插入；重放按 taskId + logicalKey 定位原计划。
 */
@Mapper
public interface PlanMapper {

    String PLAN_COLUMNS = "id, plan_id, user_id, task_id, plan_version, status, logical_key, "
            + "definition_json, signal_version, version";

    String JOB_COLUMNS = "id, job_id, user_id, task_id, plan_version, node_key, worker_key, "
            + "worker_version, status, depends_on_json, input_json, result_candidate_json, "
            + "result_json, binding_id, reused_from_job_id, cancel_requested_at, version";

    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "task_status", javaType = String.class),
            @Arg(column = "cancellation_requested", javaType = boolean.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "conversation_status", javaType = String.class)
    })
    @Select("""
            SELECT b.user_id, b.task_id, b.binding_id, t.plan_version,
                   t.status AS task_status,
                   (t.cancel_requested_at IS NOT NULL) AS cancellation_requested,
                   c.generation AS conversation_generation, c.status AS conversation_status
            FROM sq_runtime_binding b
            JOIN sq_goal_task t ON t.user_id = b.user_id AND t.task_id = b.task_id
            JOIN sq_conversation c ON c.user_id = t.user_id AND c.conversation_id = t.conversation_id
            WHERE b.user_id = #{userId} AND b.framework_session_id = #{sessionId}
              AND b.role = 'MASTER' AND c.active_task_id = t.task_id
            FOR UPDATE
            """)
    PlanContextRow lockMasterContext(@Param("userId") String userId,
                                     @Param("sessionId") String sessionId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "plan_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "logical_key", javaType = String.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "signal_version", javaType = long.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("SELECT " + PLAN_COLUMNS + " FROM sq_plan "
            + "WHERE task_id = #{taskId} AND logical_key = #{logicalKey} "
            + "ORDER BY plan_version DESC LIMIT 1")
    PlanRow findLatestByLogicalKey(@Param("taskId") String taskId,
                                   @Param("logicalKey") String logicalKey);

    @Select("SELECT plan_version FROM sq_goal_task WHERE user_id = #{userId} "
            + "AND task_id = #{taskId} FOR UPDATE")
    Long lockTaskPlanVersion(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT COUNT(*) FROM sq_goal_task
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND status IN ('QUEUED', 'RUNNING') AND cancel_requested_at IS NULL
            """)
    int countSignalEligibleTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "plan_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "logical_key", javaType = String.class),
            @Arg(column = "definition_json", javaType = String.class),
            @Arg(column = "signal_version", javaType = long.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("SELECT " + PLAN_COLUMNS + " FROM sq_plan WHERE user_id = #{userId} "
            + "AND task_id = #{taskId} AND plan_version = #{planVersion} FOR UPDATE")
    PlanRow lockPlan(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("planVersion") long planVersion);

    @Select("""
            SELECT COUNT(*) FROM sq_worker_job
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND plan_version = #{planVersion} AND status <> 'SUCCEEDED'
            """)
    int countUnfinishedJobs(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("planVersion") long planVersion);

    @Select("""
            SELECT COUNT(*) FROM sq_worker_job
            WHERE user_id = #{userId} AND task_id = #{taskId} AND plan_version = #{planVersion}
              AND status IN ('READY', 'RUNNING', 'WAITING_INPUT', 'WAITING_APPROVAL', 'RECONCILING', 'CANCELLING')
            """)
    int countActiveJobs(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("planVersion") long planVersion);

    @Select("""
            SELECT COUNT(*) FROM sq_plan WHERE user_id = #{userId} AND task_id = #{taskId}
              AND plan_version = #{planVersion} AND status = 'ACTIVE'
            """)
    int countActivePlan(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("planVersion") long planVersion);

    @Update("""
            UPDATE sq_plan SET signal_version = signal_version + 1, version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND plan_version = #{planVersion} AND status = 'ACTIVE'
            """)
    int advanceSignalVersion(@Param("userId") String userId, @Param("taskId") String taskId,
                             @Param("planVersion") long planVersion);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "node_key", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "depends_on_json", javaType = String.class),
            @Arg(column = "input_json", javaType = String.class),
            @Arg(column = "result_candidate_json", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "reused_from_job_id", javaType = String.class),
            @Arg(column = "cancel_requested_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("SELECT " + JOB_COLUMNS + " FROM sq_worker_job "
            + "WHERE task_id = #{taskId} AND plan_version = #{planVersion} ORDER BY id")
    List<WorkerJobRow> listJobs(@Param("taskId") String taskId,
                                @Param("planVersion") long planVersion);

    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "node_key", javaType = String.class),
            @Arg(column = "worker_key", javaType = String.class),
            @Arg(column = "worker_version", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "depends_on_json", javaType = String.class),
            @Arg(column = "input_json", javaType = String.class),
            @Arg(column = "result_candidate_json", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "reused_from_job_id", javaType = String.class),
            @Arg(column = "cancel_requested_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("SELECT " + JOB_COLUMNS + " FROM sq_worker_job "
            + "WHERE task_id = #{taskId} AND plan_version = #{planVersion} ORDER BY id FOR UPDATE")
    List<WorkerJobRow> lockJobs(@Param("taskId") String taskId,
                                 @Param("planVersion") long planVersion);

    @Insert("""
            INSERT INTO sq_plan
                (plan_id, user_id, task_id, plan_version, logical_key, status, definition_json, signal_version,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{planId}, #{userId}, #{taskId}, #{planVersion}, #{logicalKey}, 'ACTIVE', #{definitionJson},
                 #{signalVersion},
                 #{userId}, '', #{userId}, '')
            """)
    int insertPlan(@Param("planId") String planId, @Param("userId") String userId,
                   @Param("taskId") String taskId, @Param("planVersion") long planVersion,
                   @Param("logicalKey") String logicalKey, @Param("definitionJson") String definitionJson,
                   @Param("signalVersion") long signalVersion);

    @Insert("""
            INSERT INTO sq_worker_job
                (job_id, user_id, task_id, plan_version, node_key, worker_key, worker_version,
                 status, depends_on_json, input_json,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{jobId}, #{userId}, #{taskId}, #{planVersion}, #{nodeKey}, #{workerKey}, #{workerVersion},
                 #{status}, #{dependsOnJson}, #{inputJson},
                 #{userId}, '', #{userId}, '')
            """)
    int insertJob(@Param("jobId") String jobId, @Param("userId") String userId,
                  @Param("taskId") String taskId, @Param("planVersion") long planVersion,
                  @Param("nodeKey") String nodeKey, @Param("workerKey") String workerKey,
                  @Param("workerVersion") String workerVersion, @Param("status") String status,
                  @Param("dependsOnJson") String dependsOnJson, @Param("inputJson") String inputJson);

    @Update("""
            UPDATE sq_worker_job SET status = 'SUCCEEDED', result_json = #{resultJson},
                reused_from_job_id = #{sourceJobId}, version = version + 1
            WHERE user_id = #{userId} AND job_id = #{jobId}
              AND status IN ('READY', 'BLOCKED') AND binding_id IS NULL
            """)
    int reuseResult(@Param("userId") String userId, @Param("jobId") String jobId,
                    @Param("sourceJobId") String sourceJobId,
                    @Param("resultJson") String resultJson);

    @Update("""
            UPDATE sq_worker_job SET binding_id = #{bindingId}, version = version + 1
            WHERE user_id = #{userId} AND job_id = #{jobId}
              AND status = 'READY' AND binding_id IS NULL
            """)
    int bindReadyJob(@Param("userId") String userId, @Param("jobId") String jobId,
                     @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_worker_job SET status = 'READY', binding_id = #{bindingId},
                version = version + 1
            WHERE user_id = #{userId} AND job_id = #{jobId} AND status = 'BLOCKED'
              AND binding_id IS NULL
              AND EXISTS (SELECT 1 FROM sq_plan p WHERE p.task_id = sq_worker_job.task_id
                          AND p.plan_version = sq_worker_job.plan_version AND p.status = 'ACTIVE')
            """)
    int activateBlockedJob(@Param("userId") String userId, @Param("jobId") String jobId,
                           @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_goal_task
            SET plan_version = #{planVersion}, status = 'RUNNING', version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND plan_version = #{expectedPlanVersion}
              AND status IN ('QUEUED', 'RUNNING') AND cancel_requested_at IS NULL
            """)
    int advanceTaskPlan(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("expectedPlanVersion") long expectedPlanVersion,
                        @Param("planVersion") long planVersion);

    @Update("""
            UPDATE sq_plan SET status = 'SUPERSEDED', version = version + 1
            WHERE task_id = #{taskId} AND plan_version < #{planVersion} AND status = 'ACTIVE'
            """)
    int supersedePlans(@Param("taskId") String taskId, @Param("planVersion") long planVersion);

    @Update("""
            UPDATE sq_worker_job SET status = 'SUPERSEDED', version = version + 1
            WHERE task_id = #{taskId} AND plan_version < #{planVersion}
              AND status IN ('BLOCKED', 'READY')
            """)
    int supersedeUnstartedJobs(@Param("taskId") String taskId, @Param("planVersion") long planVersion);

    @Update("""
            UPDATE sq_command SET status = 'CANCELLED', delivery_token = NULL,
                   delivery_lease_until = NULL
            WHERE task_id = #{taskId} AND status IN ('PENDING', 'DELIVERING')
              AND binding_id IN (SELECT binding_id FROM sq_worker_job
                                 WHERE task_id = #{taskId} AND plan_version < #{planVersion}
                                   AND status = 'SUPERSEDED' AND binding_id IS NOT NULL)
            """)
    int cancelSupersededCommands(@Param("taskId") String taskId,
                                 @Param("planVersion") long planVersion);

    @Update("""
            UPDATE sq_command SET status = 'CANCELLED', delivery_token = NULL,
                   delivery_lease_until = NULL
            WHERE task_id = #{taskId} AND action = 'MASTER_SIGNAL'
              AND status IN ('PENDING', 'DELIVERING')
            """)
    int cancelSupersededSignals(@Param("taskId") String taskId);

    @Update("""
            UPDATE sq_worker_job SET status = 'SUPERSEDED',
                cancel_requested_at = CURRENT_TIMESTAMP,
                guard_token = NULL, guard_lease_until = NULL, version = version + 1
            WHERE task_id = #{taskId} AND plan_version < #{planVersion}
              AND status IN ('RUNNING', 'WAITING_INPUT', 'WAITING_APPROVAL', 'RECONCILING')
            """)
    int barrierInFlightJobs(@Param("taskId") String taskId, @Param("planVersion") long planVersion);
}
