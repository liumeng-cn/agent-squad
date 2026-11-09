package com.agentsquad.squad.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.domain.enums.RuntimeExceptionCode;

import com.agentsquad.squad.domain.dto.AcceptanceCheckView;
import com.agentsquad.squad.domain.entity.EventDO;
import com.agentsquad.squad.domain.entity.TaskDO;
import com.agentsquad.squad.domain.dto.AnswerView;
import com.agentsquad.squad.domain.response.InteractionResponse;
import com.agentsquad.squad.domain.dto.TaskResponse;
import com.agentsquad.squad.domain.response.TaskSnapshotResponse;
import com.agentsquad.squad.domain.dto.WorkerJob;
import com.agentsquad.squad.domain.response.WorkerJobPageResponse;
import com.agentsquad.squad.mapper.TaskEventMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.entity.WorkerJobDO;
import com.agentsquad.squad.mapper.PlanMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务快照服务；为详情页聚合目标状态、事件游标及当前答案占位。
 *
 * <p>快照只读取当前 Plan 的 Worker 节点；旧计划的审计记录不混入当前进度。</p>
 */
@Service
@Slf4j
public class TaskSnapshotService {

    @Resource
    private ExecutionScopeService scopeService;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private InteractionQueryService interactionQueryService;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 读取当前用户单个任务快照；任务不存在时统一按 NOT_FOUND 处理。
     *
     * <p>答案文本取当前权威修订的首个公开答案事件；无该事件时返回未完成的占位视图。
     * pending 取开放的持久交互，供详情页渲染追问卡与审批卡。</p>
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TaskSnapshotResponse snapshot(String userId, String taskId) {
        TaskDO task = taskMapper.findOwned(userId, taskId);
        if (task == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        long maxSeq = taskEventMapper.maxSeq(userId, taskId);
        TaskResponse taskResponse = new TaskResponse(task.getTaskId(), task.getConversationId(), task.getStatus(),
                task.getVersion(), task.getPlanVersion(), task.getAnswerRevision(),
                formatTime(task.getCancelRequestedAt()), task.getBlockedReason(),
                formatTime(task.getCreateTime()), formatTime(task.getLastUpdateTime()));
        AnswerView answer = buildAnswer(userId, taskId, task.getAnswerRevision());
        List<InteractionResponse> pending = interactionQueryService.listOpenByTask(userId, taskId).stream()
                .map(row -> interactionQueryService.viewOf(userId, row))
                .toList();
        String cursor = taskId + ":" + maxSeq;
        log.debug("任务快照生成 userId={} taskId={} seq={} answerCompleted={} pending={}",
                userId, taskId, maxSeq, answer.completed(), pending.size());
        return new TaskSnapshotResponse(taskResponse, goalMessage(userId, taskId),
                currentJobs(task), pending, answer, cursor);
    }

    /**
     * 子任务列表只读取归属目标当前计划；计划最多 16 个节点，无分页截断。
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public WorkerJobPageResponse listJobs(String userId, String taskId) {
        return listJobs(userId, taskId, null);
    }

    /** 按真实计划版本读取历史；权限仍以当前会话和目标归属判断，旧节点不混入当前完成数。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkerJobPageResponse listJobs(String userId, String taskId, Long requestedPlanVersion) {
        TaskDO task = taskMapper.findOwned(userId, taskId);
        if (task == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        long version = requestedPlanVersion == null ? task.getPlanVersion() : requestedPlanVersion;
        if (version < 0 || (requestedPlanVersion != null && version == 0) || version > task.getPlanVersion()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "这版工作安排不存在");
        }
        var jobs = version == 0 ? List.<WorkerJob>of() : planMapper.listJobs(taskId, version).stream().map(this::jobView).toList();
        return new WorkerJobPageResponse(jobs, null, version == task.getPlanVersion() ? acceptanceChecks(task) : List.of(),
                version, task.getPlanVersion());
    }

    /** 只展示正式结束任务的检查结论；候选检查尚未取得发布资格，不提前显示为已完成。 */
    private List<AcceptanceCheckView> acceptanceChecks(TaskDO task) {
        if (task.getPlanVersion() == 0 || !java.util.Set.of("SUCCEEDED", "PARTIAL", "FAILED").contains(task.getStatus())) {
            return List.of();
        }
        String proposal = planMapper.completionProposal(task.getUserId(), task.getTaskId(), task.getPlanVersion());
        if (proposal == null) {
            return List.of();
        }
        try {
            var checks = objectMapper.readTree(proposal).path("acceptanceResults");
            if (!checks.isArray() || checks.size() > 20) {
                return List.of();
            }
            var views = new java.util.ArrayList<AcceptanceCheckView>();
            for (var check : checks) {
                if (check.path("planVersion").asLong(-1) != task.getPlanVersion()) {
                    return List.of();
                }
                views.add(new AcceptanceCheckView(check.path("criterionId").asText(),
                        check.path("text").asText(), check.path("required").asBoolean(),
                        com.agentsquad.squad.domain.enums.AcceptanceVerificationType.valueOf(check.path("verificationType").asText()),
                        com.agentsquad.squad.domain.enums.PlanAcceptanceVerdict.valueOf(check.path("verdict").asText())));
            }
            return List.copyOf(views);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("已发布任务的检查说明无法解析", exception);
        }
    }

    /**
     * 已经完成的节点只公开经终态裁决的结果，候选与固定输入留在运行端。
     */
    private List<WorkerJob> currentJobs(TaskDO task) {
        if (task.getPlanVersion() == 0) {
            return List.of();
        }
        return planMapper.listJobs(task.getTaskId(), task.getPlanVersion()).stream()
                .map(this::jobView)
                .toList();
    }

    private WorkerJob jobView(WorkerJobDO row) {
        JsonNode result = null;
        if ("SUCCEEDED".equals(row.getStatus()) && row.getResultJson() != null) {
            try {
                result = RuntimePayloads.readJson(objectMapper, row.getResultJson());
            } catch (JsonProcessingException exception) {
                log.warn("Worker 最终结果无法解析 taskId={} jobId={}",
                        row.getTaskId(), row.getJobId(), exception);
            }
        }
        var state = com.agentsquad.squad.domain.enums.WorkerJobStatus.valueOf(row.getStatus());
        try {
            var input = RuntimePayloads.readJson(objectMapper, row.getInputJson());
            String goal = input.path("goal").asText(row.getNodeKey());
            int end = goal.offsetByCodePoints(0, Math.min(80, goal.codePointCount(0, goal.length())));
            List<String> dependencies = new java.util.ArrayList<>();
            if (row.getDependsOnJson() != null) {
                RuntimePayloads.readJson(objectMapper, row.getDependsOnJson()).forEach(name -> dependencies.add(name.asText()));
            }
            return new WorkerJob(row.getJobId(), row.getTaskId(), row.getNodeKey(), row.getPlanVersion(),
                    row.getWorkerKey(), row.getWorkerVersion(), state, row.getVersion(),
                    row.getBindingId(), row.getReusedFromJobId(), result, goal.substring(0, end), List.copyOf(dependencies),
                    state == com.agentsquad.squad.domain.enums.WorkerJobStatus.SKIPPED
                            ? com.agentsquad.squad.domain.enums.WorkerSkipReason.DEPENDENCY_FAILED : null,
                    input.path("required").isBoolean() ? input.get("required").asBoolean() : null);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("工作步骤说明无法读取", invalid);
        }
    }

    /**
     * 从规范化输入投影用户目标原文；历史数据缺失 message 时返回空串，不抛错。
     */
    private String goalMessage(String userId, String taskId) {
        String inputJson = taskMapper.findInputJson(userId, taskId);
        if (inputJson == null || inputJson.isBlank()) {
            return "";
        }
        try {
            return RuntimePayloads.readJson(objectMapper, inputJson).path("message").asText("");
        } catch (JsonProcessingException exception) {
            log.warn("目标输入载荷无法解析 userId={} taskId={}", userId, taskId, exception);
            return "";
        }
    }

    private AnswerView buildAnswer(String userId, String taskId, long answerRevision) {
        EventDO event = taskEventMapper.findLatestAnswerEvent(userId, taskId, answerRevision);
        if (event == null) {
            return new AnswerView(answerRevision, "", false);
        }
        try {
            JsonNode payload = RuntimePayloads.readJson(objectMapper, event.getPayloadJson());
            String text = payload.path("text").asText("");
            return new AnswerView(payload.path("revision").asLong(answerRevision), text,
                    "answer.completed".equals(event.getEventType()));
        } catch (JsonProcessingException exception) {
            log.warn("答案事件载荷无法解析 userId={} taskId={}", userId, taskId, exception);
            return new AnswerView(answerRevision, "", false);
        }
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
