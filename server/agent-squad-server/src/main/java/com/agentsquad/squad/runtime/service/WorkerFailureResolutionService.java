package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.enums.worker.WorkerJobStatus;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.entity.GoalTaskStatus;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.TaskDirectiveMapper;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.presentation.service.TaskEventPublishingService;
import com.agentsquad.squad.domain.dto.AbandonFailedJobRequest;
import com.agentsquad.squad.domain.dto.JobDispositionReceipt;
import com.agentsquad.squad.runtime.repository.CompletionMapper;
import com.agentsquad.squad.runtime.repository.PlanMapper;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
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
    public JobDispositionReceipt abandon(String userId, String sessionId, AbandonFailedJobRequest request) {
        if (request == null || request.jobId() == null || request.expectedPlanVersion() < 1) {
            throw conflict("工作标识或计划版本无效");
        }
        scopes.lockOwned(userId, scopes.scopeForSession(userId, sessionId), true);
        var context = completionMapper.lockByMasterSession(userId, sessionId);
        if (context == null || context.cancellationRequested()
                || !(GoalTaskStatus.RUNNING.name().equals(context.taskStatus()) || GoalTaskStatus.QUEUED.name().equals(context.taskStatus()))
                || context.planVersion() != request.expectedPlanVersion()) {
            throw conflict("只能处理当前任务和当前计划中的未完成工作");
        }
        var plan = plans.lockPlan(userId, context.taskId(), context.planVersion());
        if (plan == null || !"ACTIVE".equals(plan.status())) {
            throw conflict("工作安排已变化，请重新读取当前计划");
        }
        var job = plans.lockJobs(context.taskId(), context.planVersion()).stream()
                .filter(item -> item.jobId().equals(request.jobId())).findFirst()
                .orElseThrow(() -> conflict("这项工作不属于当前计划"));
        if (WorkerJobStatus.FAILED.name().equals(job.status())) {
            return new JobDispositionReceipt(job.jobId(), job.planVersion(), WorkerJobStatus.FAILED);
        }
        if (!WorkerJobStatus.RECOVERY_BLOCKED.name().equals(job.status())
                || commands.countPendingWorkerResumes(userId, job.bindingId()) > 0
                || completionMapper.countPendingEffects(userId, context.taskId()) > 0
                || !directives.pending(userId, context.taskId()).isEmpty()
                || directives.pendingControls(userId, context.taskId()) > 0) {
            throw conflict("这项工作仍在执行、等待处理或有结果待核对，不能直接放弃");
        }
        if (plans.abandonBlockedJob(userId, context.taskId(), job.planVersion(), job.jobId()) != 1) {
            throw conflict("工作状态已经变化，请重新读取");
        }
        dependencies.activateReadyDependents(userId, context.taskId(), job.planVersion());
        if (projections.reserveEventSequence(userId, context.taskId()) != 1) {
            throw new IllegalStateException("工作处置事件序号预留失败");
        }
        events.insertProjected(userId, context.taskId(), projections.eventSequence(userId, context.taskId()),
                "job-abandoned:" + job.jobId(), "job.failed",
                RuntimePayloads.toJson(json, Map.of("jobId", job.jobId(), "planVersion", job.planVersion(), "status", WorkerJobStatus.FAILED)),
                LocalDateTime.now(ZoneOffset.UTC));
        signals.onJobStateChanged(plan);
        log.info("已明确结束无法继续的工作 taskId={} jobId={} planVersion={} status={}",
                context.taskId(), job.jobId(), job.planVersion(), WorkerJobStatus.FAILED);
        return new JobDispositionReceipt(job.jobId(), job.planVersion(), WorkerJobStatus.FAILED);
    }

    private RuntimeBusinessException conflict(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, message);
    }
}
