package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.presentation.service.TaskEventPublishingService;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.RuntimeCommandAction;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import com.agentsquad.squad.conversation.repository.DeliveryMapper;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.integration.agentscope.NativeSubmissionReceipt;
import com.agentsquad.squad.runtime.domain.PlanRow;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.domain.WorkerLaunchRow;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.dto.PlanPublish;
import com.agentsquad.squad.runtime.repository.PlanMapper;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.repository.WorkerDispatchMapper;
import com.agentsquad.squad.worker.service.WorkerDefinitionService;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Worker 进程退出后的持久恢复协调。
 * <p>
 * 仅 SDK 确认原命令处于 interrupted 且当前业务 guard 有效时，写入原 Session/Turn
 * 的 RESUME 意图；成功分支和已终态节点不生成新命令，也不以新 SUBMIT 冒充恢复。
 */
@Service
@Slf4j
public class WorkerRecoveryService {

    @Resource
    private TaskEventPublishingService conversationProjection;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private WorkerDispatchMapper dispatchMapper;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private DeliveryMapper deliveryMapper;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private WorkerDefinitionService workerDefinitionService;

    @Resource
    private WorkerProfileRegistry profileRegistry;

    @Resource
    private MasterSignalService masterSignalService;

    /**
     * 投影器仅对当前运行中 Worker 检查原始 SUBMIT 的 SDK 收据。
     */
    public String originalSubmitCommandId(ProjectionTarget target) {
        WorkerLaunchRow job = dispatchMapper.findByBinding(target.userId(), target.bindingId());
        if (job == null || !"RUNNING".equals(job.jobStatus())
                || !"ACTIVE".equals(job.planStatus()) || job.cancelRequestedAt() != null) {
            return null;
        }
        return commandMapper.findWorkerSubmitCommandId(target.userId(), target.bindingId());
    }

    /**
     * SDK 收据来自固定 Session 的原 Command；重新锁定 Task/Plan/Job 后幂等写入恢复意图。
     */
    @Transactional
    public void queueInterrupted(ProjectionTarget target, NativeSubmissionReceipt receipt) {
        if (receipt == null || !"interrupted".equals(receipt.status())
                || receipt.turnId() == null || receipt.turnId().isBlank()) {
            return;
        }
        taskMapper.lockOwned(target.userId(), target.taskId());
        Long currentVersion = planMapper.lockTaskPlanVersion(target.userId(), target.taskId());
        WorkerLaunchRow job = dispatchMapper.findByBinding(target.userId(), target.bindingId());
        if (job == null || currentVersion == null || currentVersion != job.planVersion()
                || planMapper.countSignalEligibleTask(target.userId(), target.taskId()) == 0
                || !"RUNNING".equals(job.jobStatus()) || job.cancelRequestedAt() != null
                || job.guardToken() == null || job.guardLeaseUntil() == null
                || !job.guardLeaseUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        PlanRow plan = planMapper.lockPlan(target.userId(), target.taskId(), job.planVersion());
        if (plan == null || !"ACTIVE".equals(plan.status())) {
            return;
        }
        String scopeKey = "worker-resume:" + job.jobId();
        String recoveryKey = receipt.turnId() + ":" + job.guardToken();
        if (commandMapper.findByKey(target.userId(), scopeKey, recoveryKey) != null
                || commandMapper.countPendingWorkerResumes(target.userId(), target.bindingId()) > 0) {
            return;
        }
        PlanPublish definition;
        try {
            definition = objectMapper.treeToValue(RuntimePayloads.readJson(objectMapper, plan.definitionJson()),
                    PlanPublish.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Worker 恢复计划解析失败，无法读取节点恢复上限");
        }
        PlanNode node = definition.nodes().stream().filter(item -> item.nodeKey().equals(job.nodeKey()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Worker 恢复节点不存在"));
        String profileKey = workerDefinitionService.requirePublished(job.workerKey(), job.workerVersion())
                .runtimeProfileKey();
        int limit = Math.min(node.maxRecoveryAttempts(), profileRegistry.requireRuntime(profileKey)
                .getMaxRecoveryAttempts());
        if (commandMapper.countWorkerResumes(target.userId(), target.bindingId()) >= limit) {
            if (dispatchMapper.markRecoveryBlocked(target.userId(), target.bindingId()) == 1) {
                masterSignalService.onJobStateChanged(plan);
                if (projectionMapper.reserveEventSequence(target.userId(), target.taskId()) != 1) {
                    throw new IllegalStateException("Worker 恢复阻塞事件序号保留失败");
                }
                conversationProjection.insertProjected(target.userId(), target.taskId(),
                        projectionMapper.eventSequence(target.userId(), target.taskId()),
                        "worker-resume:" + job.jobId() + ":limit", "job.recovery_blocked",
                        RuntimePayloads.toJson(objectMapper, Map.of(
                                "jobId", job.jobId(), "errorCode", "RECOVERY_LIMIT_REACHED")),
                        LocalDateTime.now(ZoneOffset.UTC));
            }
            log.warn("Worker 恢复次数已耗尽 taskId={} jobId={} limit={}", target.taskId(), job.jobId(), limit);
            return;
        }
        String payload = RuntimePayloads.toJson(objectMapper, Map.of("turnId", receipt.turnId()));
        String commandId = UUID.randomUUID().toString();
        commandMapper.insertControl(commandId, target.userId(), scopeKey, recoveryKey,
                ContentDigests.sha256(payload), target.taskId(), target.bindingId(),
                RuntimeCommandAction.RESUME.name(), payload, LocalDateTime.now(ZoneOffset.UTC));
        log.info("Worker 原生恢复命令已排入 taskId={} jobId={} commandId={} turnId={}",
                target.taskId(), job.jobId(), commandId, receipt.turnId());
    }

    /**
     * SDK 明确拒绝或有界重试耗尽时，只阻塞当前失败分支并保存候选，父目标保持可核对。
     */
    @Transactional
    public void blockRejectedResume(String userId, String taskId, String bindingId,
                                    String commandId, String deliveryToken, String errorCode) {
        taskMapper.lockOwned(userId, taskId);
        Long currentVersion = planMapper.lockTaskPlanVersion(userId, taskId);
        if (deliveryMapper.blockRejectedResume(commandId, deliveryToken, errorCode) != 1) {
            return;
        }
        WorkerLaunchRow job = dispatchMapper.findByBinding(userId, bindingId);
        PlanRow plan = currentVersion == null ? null : planMapper.lockPlan(userId, taskId, currentVersion);
        if (job != null && plan != null && job.planVersion() == currentVersion && "ACTIVE".equals(plan.status())
                && dispatchMapper.markRecoveryBlocked(userId, bindingId) == 1) {
            masterSignalService.onJobStateChanged(plan);
            if (projectionMapper.reserveEventSequence(userId, taskId) != 1) {
                throw new IllegalStateException("Worker 恢复阻塞事件序号保留失败");
            }
            conversationProjection.insertProjected(userId, taskId,
                    projectionMapper.eventSequence(userId, taskId),
                    "worker-resume:" + commandId + ":blocked", "job.recovery_blocked",
                    RuntimePayloads.toJson(objectMapper, Map.of(
                            "jobId", job.jobId(), "errorCode", errorCode)),
                    LocalDateTime.now(ZoneOffset.UTC));
        }
        log.warn("Worker 原生恢复命令被阻塞 taskId={} jobId={} commandId={} errorCode={}",
                taskId, job == null ? null : job.jobId(), commandId, errorCode);
    }

    /**
     * 原生回合已结束时只关闭多余的恢复命令，终态交给同一原生日志的 run/end 投影裁决。
     */
    public void discardSettledResume(String commandId, String deliveryToken) {
        deliveryMapper.blockRejectedResume(commandId, deliveryToken, "NATIVE_TURN_SETTLED");
    }
}
