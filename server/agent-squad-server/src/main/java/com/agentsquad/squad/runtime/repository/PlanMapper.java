package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.PlanContextRow;
import com.agentsquad.squad.runtime.domain.PlanRow;
import com.agentsquad.squad.runtime.domain.WorkerJobRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 协作计划与委派节点的 SQL 边界。
 * <p>
 * 计划头与全部节点在同一平台事务内插入；重放按 taskId + logicalKey 定位原计划。
 */
@Mapper
public interface PlanMapper {

    String PLAN_COLUMNS = "id, plan_id, user_id, task_id, plan_version, status, logical_key, "
            + "definition_json, signal_version, version, goal_revision";

    String JOB_COLUMNS = "id, job_id, user_id, task_id, plan_version, node_key, worker_key, "
            + "worker_version, status, depends_on_json, input_json, result_candidate_json, "
            + "result_json, binding_id, reused_from_job_id, cancel_requested_at, version";

    PlanContextRow lockMasterContext(@Param("userId") String userId,
                                     @Param("sessionId") String sessionId);

    PlanRow findLatestByLogicalKey(@Param("taskId") String taskId,
                                   @Param("logicalKey") String logicalKey);

    Long lockTaskPlanVersion(@Param("userId") String userId, @Param("taskId") String taskId);

    int countSignalEligibleTask(@Param("userId") String userId, @Param("taskId") String taskId);

    PlanRow lockPlan(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("planVersion") long planVersion);

    int countUnfinishedJobs(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("planVersion") long planVersion);

    int countActiveJobs(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("planVersion") long planVersion);

    int countActivePlan(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("planVersion") long planVersion);

    int advanceSignalVersion(@Param("userId") String userId, @Param("taskId") String taskId,
                             @Param("planVersion") long planVersion);

    List<WorkerJobRow> listJobs(@Param("taskId") String taskId,
                                @Param("planVersion") long planVersion);

    List<WorkerJobRow> lockJobs(@Param("taskId") String taskId,
                                 @Param("planVersion") long planVersion);

    int insertPlan(@Param("planId") String planId, @Param("userId") String userId,
                   @Param("taskId") String taskId, @Param("planVersion") long planVersion,
                   @Param("logicalKey") String logicalKey, @Param("definitionJson") String definitionJson,
                   @Param("signalVersion") long signalVersion, @Param("goalRevision") long goalRevision);

    int insertJob(@Param("jobId") String jobId, @Param("userId") String userId,
                  @Param("taskId") String taskId, @Param("planVersion") long planVersion,
                  @Param("nodeKey") String nodeKey, @Param("workerKey") String workerKey,
                  @Param("workerVersion") String workerVersion, @Param("status") String status,
                  @Param("dependsOnJson") String dependsOnJson, @Param("inputJson") String inputJson);

    int reuseResult(@Param("userId") String userId, @Param("jobId") String jobId,
                    @Param("sourceJobId") String sourceJobId,
                    @Param("resultJson") String resultJson);

    int bindReadyJob(@Param("userId") String userId, @Param("jobId") String jobId,
                     @Param("bindingId") String bindingId);

    int activateBlockedJob(@Param("userId") String userId, @Param("jobId") String jobId,
                           @Param("bindingId") String bindingId);

    int advanceTaskPlan(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("expectedPlanVersion") long expectedPlanVersion,
                        @Param("planVersion") long planVersion);

    int supersedePlans(@Param("taskId") String taskId, @Param("planVersion") long planVersion);

    int supersedeUnstartedJobs(@Param("taskId") String taskId, @Param("planVersion") long planVersion);

    int cancelSupersededCommands(@Param("taskId") String taskId,
                                 @Param("planVersion") long planVersion);

    int cancelSupersededSignals(@Param("taskId") String taskId);

    int barrierInFlightJobs(@Param("taskId") String taskId, @Param("planVersion") long planVersion);
}
