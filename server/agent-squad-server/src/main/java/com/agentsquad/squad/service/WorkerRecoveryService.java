package com.agentsquad.squad.service;

import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.enums.RuntimeCommandAction;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.mapper.DeliveryMapper;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.integration.agentscope.NativeSubmissionReceipt;
import com.agentsquad.squad.domain.entity.PlanDO;
import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import com.agentsquad.squad.domain.entity.WorkerLaunchDO;
import com.agentsquad.squad.domain.dto.PlanNode;
import com.agentsquad.squad.domain.dto.PlanPublish;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.mapper.ProjectionMapper;
import com.agentsquad.squad.mapper.WorkerDispatchMapper;
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
    public String originalSubmitCommandId(ProjectionTargetDO target) {
        WorkerLaunchDO job = dispatchMapper.findByBinding(target.getUserId(), target.getBindingId());
        if (job == null || !"RUNNING".equals(job.getJobStatus())
                || !"ACTIVE".equals(job.getPlanStatus()) || job.getCancelRequestedAt() != null) {
            return null;
        }
        return commandMapper.findWorkerSubmitCommandId(target.getUserId(), target.getBindingId());
    }

    /**
     * SDK 收据来自固定 Session 的原 Command；重新锁定 Task/Plan/Job 后幂等写入恢复意图。
     */
    @Transactional
    public void queueInterrupted(ProjectionTargetDO target, NativeSubmissionReceipt receipt) {
        if (receipt == null || !"interrupted".equals(receipt.status())
                || receipt.turnId() == null || receipt.turnId().isBlank()) {
            return;
        }
        taskMapper.lockOwned(target.getUserId(), target.getTaskId());
        Long currentVersion = planMapper.lockTaskPlanVersion(target.getUserId(), target.getTaskId());
        WorkerLaunchDO job = dispatchMapper.findByBinding(target.getUserId(), target.getBindingId());
        if (job == null || currentVersion == null || currentVersion != job.getPlanVersion()
                || planMapper.countSignalEligibleTask(target.getUserId(), target.getTaskId()) == 0
                || !"RUNNING".equals(job.getJobStatus()) || job.getCancelRequestedAt() != null
                || job.getGuardToken() == null || job.getGuardLeaseUntil() == null
                || !job.getGuardLeaseUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        PlanDO plan = planMapper.lockPlan(target.getUserId(), target.getTaskId(), job.getPlanVersion());
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) {
            return;
        }
        String scopeKey = "worker-resume:" + job.getJobId();
        String recoveryKey = receipt.turnId() + ":" + job.getGuardToken();
        if (commandMapper.findByKey(target.getUserId(), scopeKey, recoveryKey) != null
                || commandMapper.countPendingWorkerResumes(target.getUserId(), target.getBindingId()) > 0) {
            return;
        }
        PlanPublish definition;
        try {
            definition = objectMapper.treeToValue(RuntimePayloads.readJson(objectMapper, plan.getDefinitionJson()),
                    PlanPublish.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Worker 恢复计划解析失败，无法读取节点恢复上限");
        }
        PlanNode node = definition.nodes().stream().filter(item -> item.nodeKey().equals(job.getNodeKey()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Worker 恢复节点不存在"));
        String profileKey = workerDefinitionService.requirePublished(job.getWorkerKey(), job.getWorkerVersion())
                .runtimeProfileKey();
        int limit = Math.min(node.maxRecoveryAttempts(), profileRegistry.requireRuntime(profileKey)
                .getMaxRecoveryAttempts());
        if (commandMapper.countWorkerResumes(target.getUserId(), target.getBindingId()) >= limit) {
            if (dispatchMapper.markRecoveryBlocked(target.getUserId(), target.getBindingId()) == 1) {
                masterSignalService.onJobStateChanged(plan);
                if (projectionMapper.reserveEventSequence(target.getUserId(), target.getTaskId()) != 1) {
                    throw new IllegalStateException("Worker 恢复阻塞事件序号保留失败");
                }
                conversationProjection.insertProjected(target.getUserId(), target.getTaskId(),
                        projectionMapper.eventSequence(target.getUserId(), target.getTaskId()),
                        "worker-resume:" + job.getJobId() + ":limit", "job.recovery_blocked",
                        RuntimePayloads.toJson(objectMapper, Map.of(
                                "jobId", job.getJobId(), "errorCode", "RECOVERY_LIMIT_REACHED")),
                        LocalDateTime.now(ZoneOffset.UTC));
            }
            log.warn("Worker 恢复次数已耗尽 taskId={} jobId={} limit={}", target.getTaskId(), job.getJobId(), limit);
            return;
        }
        String payload = RuntimePayloads.toJson(objectMapper, Map.of("turnId", receipt.turnId()));
        String commandId = UUID.randomUUID().toString();
        commandMapper.insertControl(commandId, target.getUserId(), scopeKey, recoveryKey,
                ContentDigests.sha256(payload), target.getTaskId(), target.getBindingId(),
                RuntimeCommandAction.RESUME.name(), payload, LocalDateTime.now(ZoneOffset.UTC));
        log.info("Worker 原生恢复命令已排入 taskId={} jobId={} commandId={} turnId={}",
                target.getTaskId(), job.getJobId(), commandId, receipt.turnId());
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
        WorkerLaunchDO job = dispatchMapper.findByBinding(userId, bindingId);
        PlanDO plan = currentVersion == null ? null : planMapper.lockPlan(userId, taskId, currentVersion);
        if (job != null && plan != null && job.getPlanVersion() == currentVersion && "ACTIVE".equals(plan.getStatus())
                && dispatchMapper.markRecoveryBlocked(userId, bindingId) == 1) {
            masterSignalService.onJobStateChanged(plan);
            if (projectionMapper.reserveEventSequence(userId, taskId) != 1) {
                throw new IllegalStateException("Worker 恢复阻塞事件序号保留失败");
            }
            conversationProjection.insertProjected(userId, taskId,
                    projectionMapper.eventSequence(userId, taskId),
                    "worker-resume:" + commandId + ":blocked", "job.recovery_blocked",
                    RuntimePayloads.toJson(objectMapper, Map.of(
                            "jobId", job.getJobId(), "errorCode", errorCode)),
                    LocalDateTime.now(ZoneOffset.UTC));
        }
        log.warn("Worker 原生恢复命令被阻塞 taskId={} jobId={} commandId={} errorCode={}",
                taskId, job == null ? null : job.getJobId(), commandId, errorCode);
    }

    /**
     * 原生回合已结束时只关闭多余的恢复命令，终态交给同一原生日志的 run/end 投影裁决。
     */
    public void discardSettledResume(String commandId, String deliveryToken) {
        deliveryMapper.blockRejectedResume(commandId, deliveryToken, "NATIVE_TURN_SETTLED");
    }
}
