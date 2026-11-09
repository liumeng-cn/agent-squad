package com.agentsquad.squad.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.enums.WorkerJobStatus;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.enums.PlanJoinStage;
import com.agentsquad.squad.enums.RuntimeCommandAction;
import com.agentsquad.squad.mapper.BindingMapper;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.model.po.PlanPO;
import com.agentsquad.squad.model.po.WorkerJobPO;
import com.agentsquad.squad.mapper.PlanMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Worker 结果到 Master 协调轮次的持久信号服务。
 * <p>
 * 每个完成或恢复受限的 Job 推进当前 Plan 的 signalVersion；全部节点成功或无活动分支时仅建立一条
 * MASTER_SIGNAL 命令，固定 Job 引用和结果内容由原生会话按命令 ID 幂等受理。
 */
@Service
@Slf4j
public class MasterSignalService {

    @Resource
    private TaskDirectiveMapper directives;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private BindingMapper bindingMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PlanCompletionEvaluationService planEvaluation;

    @Resource
    private PlanAcceptanceService acceptance;

    /**
     * 调用方已锁定父 Task 和 Plan；结果、信号版本与命令在同一平台事务内提交。
     */
    public void onJobStateChanged(PlanPO plan) {
        if (planMapper.advanceSignalVersion(plan.getUserId(), plan.getTaskId(), plan.getPlanVersion()) != 1) {
            throw new IllegalStateException("计划汇合版本推进失败");
        }
        createIfReady(plan);
    }

    /**
     * 原生 Master 回合结束并完成投影后，补发因协调回合未结束而暂缓的汇合命令。
     */
    public void onMasterTurnEnded(Long userId, Long taskId, String turnId) {
        if (turnId == null || turnId.isBlank()) {
            return;
        }
        commandMapper.markMasterTurnApplied(userId, taskId, turnId);
        Long planVersion = planMapper.lockTaskPlanVersion(userId, taskId);
        if (planVersion == null || planVersion == 0) {
            return;
        }
        PlanPO plan = planMapper.lockPlan(userId, taskId, planVersion);
        if (plan != null && "ACTIVE".equals(plan.getStatus())) {
            createIfReady(plan);
        }
    }

    private void createIfReady(PlanPO plan) {
        if (!directives.pending(plan.getUserId(), plan.getTaskId()).isEmpty()
                || directives.pendingControls(plan.getUserId(), plan.getTaskId()) > 0) {
            return;
        }
        if (planMapper.countSignalEligibleTask(plan.getUserId(), plan.getTaskId()) == 0) {
            return;
        }
        List<WorkerJobPO> jobs = planMapper.lockJobs(plan.getTaskId(), plan.getPlanVersion());
        boolean allSucceeded = !jobs.isEmpty() && jobs.stream().allMatch(job -> WorkerJobStatus.SUCCEEDED.name().equals(job.getStatus()));
        var stage = planEvaluation.evaluate(plan.getUserId(), plan.getTaskId(), plan.getPlanVersion(), List.of()).decision().stage();
        if (jobs.isEmpty() || !(stage == PlanJoinStage.READY
                || stage == PlanJoinStage.NEEDS_COORDINATION)
                || commandMapper.countUnprocessedMasterCommands(plan.getUserId(), plan.getTaskId()) > 0) {
            return;
        }
        long signalVersion = planMapper.lockPlan(plan.getUserId(), plan.getTaskId(), plan.getPlanVersion())
                .getSignalVersion();
        String scopeKey = "master-signal:" + plan.getTaskId();
        if (commandMapper.findByKey(plan.getUserId(), scopeKey, String.valueOf(signalVersion)) != null) {
            return;
        }
        Long bindingId = bindingMapper.findMasterBindingId(plan.getUserId(), plan.getTaskId());
        if (bindingId == null) {
            throw new IllegalStateException("汇合时 Master Binding 已消失");
        }
        List<Map<String, Object>> results = new ArrayList<>(jobs.size());
        var definition = acceptance.definition(plan);
        for (WorkerJobPO job : jobs) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", job.getJobId());
            result.put("nodeKey", job.getNodeKey());
            result.put("workerKey", job.getWorkerKey());
            result.put("workerVersion", job.getWorkerVersion());
            result.put("status", job.getStatus());
            definition.nodes().stream().filter(node -> node.nodeKey().equals(job.getNodeKey()))
                    .findFirst().ifPresent(node -> result.put("planNode", node));
            if ("SUCCEEDED".equals(job.getStatus())) {
                result.put("result", readResult(job));
            }
            results.add(result);
        }
        String message = "内部协作结果，属于当前用户目标的同一次任务，不是新的用户请求。"
                + "当前计划版本为 " + plan.getPlanVersion() + "。请核对以下固定 Worker 状态与成功结果。"
                + (allSucceeded ? "完成原目标并调用 propose_completion："
                : "有工作未完成。RECOVERY_BLOCKED不是最终失败：优先发布新计划重新安排；确认不能继续时先调用abandon_failed_job明确放弃。"
                + "publish_plan完整替换当前计划，不是追加节点；新计划必须包含原目标仍需要的成功节点和待补做节点。"
                + "成功节点可沿用下面planNode中的固定输入，平台判定能否复用；不要只提交补做部分导致已有成果退出当前计划。"
                + "全部工作明确收尾后可用PARTIAL说明未完成部分，不能把等待恢复的工作直接当作完成：")
                + RuntimePayloads.toJson(objectMapper, results)
                + "。当前完成条件：" + RuntimePayloads.toJson(objectMapper, definition.acceptanceCriteria())
                + "。propose_completion必须提供acceptanceReviews，逐项criterionId、PASS/FAIL/UNKNOWN和evidenceRefs。"
                + "MODEL_REVIEW引用本计划成功jobId并如实评价；USER_REVIEW先调用request_acceptance_review，"
                + "收到实际答复后引用interactionId；缺少证据不能声明SUCCEEDED。";
        String payload = RuntimePayloads.toJson(objectMapper, Map.of(
                "message", message,
                "planVersion", plan.getPlanVersion(),
                "signalVersion", signalVersion));
        Long commandId = IdWorker.getId();
        commandMapper.insertControl(commandId, plan.getUserId(),
                scopeKey,
                String.valueOf(signalVersion),
                ContentDigests.sha256(payload), plan.getTaskId(), bindingId,
                RuntimeCommandAction.MASTER_SIGNAL.name(), payload,
                LocalDateTime.now(ZoneOffset.UTC));
        log.info("Master 汇合命令已创建 taskId={} planVersion={} signalVersion={} commandId={} jobCount={}",
                plan.getTaskId(), plan.getPlanVersion(), signalVersion, commandId, jobs.size());
    }

    private JsonNode readResult(WorkerJobPO job) {
        try {
            return RuntimePayloads.readJson(objectMapper, job.getResultJson());
        } catch (Exception exception) {
            throw new IllegalStateException("Worker 最终结果无法读取: " + job.getJobId(), exception);
        }
    }
}
