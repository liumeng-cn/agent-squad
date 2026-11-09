package com.agentsquad.squad.service;

import com.agentsquad.squad.enums.WorkerJobStatus;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.enums.GoalTaskStatus;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.bo.AbandonFailedJobRequestBO;
import com.agentsquad.squad.bo.JobDispositionReceiptBO;
import com.agentsquad.squad.mapper.CompletionMapper;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * 恢复受限工作的明确收尾
 * <p>
 * 只有当前目标的协调会话可决定放弃；正在运行、待恢复投递、未处理用户要求和未知外部影响均阻止放弃。
 * 失败状态、下游跳过、公开事件及协调通知在同一事务保存，重复调用不重复推进版本。
 */
@Slf4j
@Service
public class WorkerFailureResolutionService {
    @Resource
    private ExecutionScopeService scopes;
    @Resource
    private CompletionMapper completionMapper;
    @Resource
    private PlanMapper plans;
    @Resource
    private CommandMapper commands;
    @Resource
    private TaskDirectiveMapper directives;
    @Resource
    private WorkerDependencyService dependencies;
    @Resource
    private MasterSignalService signals;
    @Resource
    private ProjectionMapper projections;
    @Resource
    private TaskEventPublishingService events;
    @Resource
    private ObjectMapper json;

    @Transactional
    public JobDispositionReceiptBO abandon(String userId, String sessionId, AbandonFailedJobRequestBO request) {
        if (request == null || request.jobId() == null || request.expectedPlanVersion() < 1) {
            throw conflict("工作标识或计划版本无效");
        }
        scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        var context = completionMapper.lockByMasterSession(userId, sessionId);
        if (context == null || context.isCancellationRequested()
                || !(GoalTaskStatus.RUNNING.name().equals(context.getTaskStatus()) || GoalTaskStatus.QUEUED.name().equals(context.getTaskStatus()))
                || context.getPlanVersion() != request.expectedPlanVersion()) {
            throw conflict("只能处理当前任务和当前计划中的未完成工作");
        }
        var plan = plans.lockPlan(userId, context.getTaskId(), context.getPlanVersion());
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) {
            throw conflict("工作安排已变化，请重新读取当前计划");
        }
        var job = plans.lockJobs(context.getTaskId(), context.getPlanVersion()).stream()
                .filter(item -> item.getJobId().equals(request.jobId())).findFirst()
                .orElseThrow(() -> conflict("这项工作不属于当前计划"));
        if (WorkerJobStatus.FAILED.name().equals(job.getStatus())) {
            return new JobDispositionReceiptBO(job.getJobId(), job.getPlanVersion(), WorkerJobStatus.FAILED);
        }
        if (!WorkerJobStatus.RECOVERY_BLOCKED.name().equals(job.getStatus())
                || commands.countPendingWorkerResumes(userId, job.getBindingId()) > 0
                || completionMapper.countPendingEffects(userId, context.getTaskId()) > 0
                || !directives.pending(userId, context.getTaskId()).isEmpty()
                || directives.pendingControls(userId, context.getTaskId()) > 0) {
            throw conflict("这项工作仍在执行、等待处理或有结果待核对，不能直接放弃");
        }
        if (plans.abandonBlockedJob(userId, context.getTaskId(), job.getPlanVersion(), job.getJobId()) != 1) {
            throw conflict("工作状态已经变化，请重新读取");
        }
        dependencies.activateReadyDependents(userId, context.getTaskId(), job.getPlanVersion());
        if (projections.reserveEventSequence(userId, context.getTaskId()) != 1) {
            throw new IllegalStateException("工作处置事件序号预留失败");
        }
        events.insertProjected(userId, context.getTaskId(), projections.eventSequence(userId, context.getTaskId()),
                "job-abandoned:" + job.getJobId(), "job.failed",
                RuntimePayloads.toJson(json, Map.of("jobId", job.getJobId(), "planVersion", job.getPlanVersion(), "status", WorkerJobStatus.FAILED)),
                LocalDateTime.now(ZoneOffset.UTC));
        signals.onJobStateChanged(plan);
        log.info("已明确结束无法继续的工作 taskId={} jobId={} planVersion={} status={}",
                context.getTaskId(), job.getJobId(), job.getPlanVersion(), WorkerJobStatus.FAILED);
        return new JobDispositionReceiptBO(job.getJobId(), job.getPlanVersion(), WorkerJobStatus.FAILED);
    }

    private RuntimeBusinessException conflict(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, message);
    }
}
