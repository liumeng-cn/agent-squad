package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.RuntimeCommandAction;
import com.agentsquad.squad.conversation.repository.BindingMapper;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.runtime.domain.PlanRow;
import com.agentsquad.squad.runtime.domain.WorkerJobRow;
import com.agentsquad.squad.runtime.repository.PlanMapper;
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
import java.util.Set;

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
    private PlanMapper planMapper;

    @Resource
    private BindingMapper bindingMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 调用方已锁定父 Task 和 Plan；结果、信号版本与命令在同一平台事务内提交。
     */
    public void onJobStateChanged(PlanRow plan) {
        if (planMapper.advanceSignalVersion(plan.userId(), plan.taskId(), plan.planVersion()) != 1) {
            throw new IllegalStateException("计划汇合版本推进失败");
        }
        createIfReady(plan);
    }

    /**
     * 原生 Master 回合结束并完成投影后，补发因协调回合未结束而暂缓的汇合命令。
     */
    public void onMasterTurnEnded(String userId, String taskId, String turnId) {
        if (turnId == null || turnId.isBlank()) {
            return;
        }
        commandMapper.markMasterTurnApplied(userId, taskId, turnId);
        Long planVersion = planMapper.lockTaskPlanVersion(userId, taskId);
        if (planVersion == null || planVersion == 0) {
            return;
        }
        PlanRow plan = planMapper.lockPlan(userId, taskId, planVersion);
        if (plan != null && "ACTIVE".equals(plan.status())) {
            createIfReady(plan);
        }
    }

    private void createIfReady(PlanRow plan) {
        if (planMapper.countSignalEligibleTask(plan.userId(), plan.taskId()) == 0) {
            return;
        }
        List<WorkerJobRow> jobs = planMapper.lockJobs(plan.taskId(), plan.planVersion());
        boolean allSucceeded = !jobs.isEmpty() && jobs.stream().allMatch(job -> "SUCCEEDED".equals(job.status()));
        boolean failedBranch = jobs.stream().anyMatch(job -> Set.of("RECOVERY_BLOCKED", "FAILED", "PARTIAL").contains(job.status()));
        boolean activeBranch = jobs.stream().anyMatch(job -> Set.of("READY", "RUNNING", "WAITING_INPUT",
                "WAITING_APPROVAL", "RECONCILING", "CANCELLING").contains(job.status()));
        if (jobs.isEmpty() || !(allSucceeded || (failedBranch && !activeBranch))
                || commandMapper.countUnprocessedMasterCommands(plan.userId(), plan.taskId()) > 0) {
            return;
        }
        long signalVersion = planMapper.lockPlan(plan.userId(), plan.taskId(), plan.planVersion())
                .signalVersion();
        String scopeKey = "master-signal:" + plan.taskId();
        if (commandMapper.findByKey(plan.userId(), scopeKey, String.valueOf(signalVersion)) != null) {
            return;
        }
        String bindingId = bindingMapper.findMasterBindingId(plan.userId(), plan.taskId());
        if (bindingId == null) {
            throw new IllegalStateException("汇合时 Master Binding 已消失");
        }
        List<Map<String, Object>> results = new ArrayList<>(jobs.size());
        for (WorkerJobRow job : jobs) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", job.jobId());
            result.put("nodeKey", job.nodeKey());
            result.put("workerKey", job.workerKey());
            result.put("workerVersion", job.workerVersion());
            result.put("status", job.status());
            if ("SUCCEEDED".equals(job.status())) {
                result.put("result", readResult(job));
            }
            results.add(result);
        }
        String message = "内部协作结果，属于当前用户目标的同一次任务，不是新的用户请求。"
                + "当前计划版本为 " + plan.planVersion() + "。请核对以下固定 Worker 状态与成功结果。"
                + (allSucceeded ? "完成原目标并调用 propose_completion："
                : "有分支执行失败或恢复受限；可发布新计划重规划，或以 PARTIAL 明确说明未完成部分，不能声称全部成功：")
                + RuntimePayloads.toJson(objectMapper, results);
        String payload = RuntimePayloads.toJson(objectMapper, Map.of(
                "message", message,
                "planVersion", plan.planVersion(),
                "signalVersion", signalVersion));
        String commandId = UUID.randomUUID().toString();
        commandMapper.insertControl(commandId, plan.userId(),
                scopeKey,
                String.valueOf(signalVersion),
                ContentDigests.sha256(payload), plan.taskId(), bindingId,
                RuntimeCommandAction.MASTER_SIGNAL.name(), payload,
                LocalDateTime.now(ZoneOffset.UTC));
        log.info("Master 汇合命令已创建 taskId={} planVersion={} signalVersion={} commandId={} jobCount={}",
                plan.taskId(), plan.planVersion(), signalVersion, commandId, jobs.size());
    }

    private JsonNode readResult(WorkerJobRow job) {
        try {
            return RuntimePayloads.readJson(objectMapper, job.resultJson());
        } catch (Exception exception) {
            throw new IllegalStateException("Worker 最终结果无法读取: " + job.jobId(), exception);
        }
    }
}
