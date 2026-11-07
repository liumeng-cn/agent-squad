package com.agentsquad.squad.service;

import com.agentsquad.squad.common.enums.execution.ActivityGroup;
import com.agentsquad.squad.common.enums.execution.ActivityStatus;
import com.agentsquad.squad.common.enums.execution.ActivityType;
import com.agentsquad.squad.common.enums.execution.ActivityWaitReason;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.domain.entity.ExecutionScopeDO;
import com.agentsquad.squad.mapper.CommandMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.domain.model.ActivityBusinessFact;
import com.agentsquad.squad.domain.model.ActivityClassification;
import com.agentsquad.squad.domain.model.ActivityProjectionChange;
import com.agentsquad.squad.integration.agentscope.NativeEventView;
import com.agentsquad.squad.domain.entity.ExecutionActivityDO;
import com.agentsquad.squad.domain.dto.ActivityUpdatedEvent;
import com.agentsquad.squad.domain.dto.ExecutionActivityView;
import com.agentsquad.squad.mapper.ExecutionActivityMapper;
import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import com.agentsquad.squad.mapper.WorkerDispatchMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 已提交原生事实的业务活动转换
 * <p>
 * 与业务消费水位同事务提交，模型和工具仅在真实边界推进；只提取调用ID、注册名与结果状态。
 * 活动服务不访问模型或工具，不反向决定任务完成，不保存原始上下文和结果正文。
 */
@Service
public class ActivityProjectionService {
    @Resource
    private ExecutionActivityMapper activities;

    @Resource
    private ConversationEventWriterService events;

    @Resource
    private WorkerDispatchMapper workers;

    @Resource
    private TaskMapper tasks;

    @Resource
    private CommandMapper commands;

    /**
     * 连续消费事务已校验归属与原生顺序；重复事件不再调用本入口。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void accept(ExecutionScopeDO scope, ProjectionTargetDO target, NativeEventView event) {
        if ("run/start".equals(event.type()) && workers.findByBinding(scope.userId(), target.bindingId()) != null) {
            completed(scope, target.bindingId(), new ActivityBusinessFact(ActivityType.WORKER_DISPATCH, event.runId()));
        }
        if ("run/end".equals(event.type())) {
            closeRun(scope, target, event);
            return;
        }
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        if (List.of("request/prepared", "model/dispatch", "model/end").contains(event.type())) {
            String id = text(data.get("modelCallId"));
            if (id == null) {
                return;
            }
            var classification = new ActivityClassification(ActivityType.MODEL_CALL, ActivityGroup.EXECUTION, "模型处理");
            if (scope.kind().equals("TASK") && commands.countSignalTurn(scope.userId(), scope.ownerId(), event.turnId()) > 0) {
                classification = new ActivityClassification(ActivityType.RESULT_AGGREGATE, ActivityGroup.DELIVERY, "汇总结果");
            }
            ActivityStatus status = switch (event.type()) {
                case "request/prepared" -> ActivityStatus.QUEUED;
                case "model/dispatch" -> ActivityStatus.RUNNING;
                default -> resultStatus(text(data.get("status")));
            };
            save(scope, target, event, new ActivityProjectionChange("model:" + id, classification, status, null));
            return;
        }
        String id = text(data.get("toolCallId"));
        String name = text(data.get("name"));
        if (data.get("call") instanceof Map<?, ?> call) {
            id = text(call.get("id"));
            name = text(call.get("name"));
        }
        if ("tool/result".equals(event.type()) && data.get("message") instanceof Map<?, ?> message
                && message.get("content") instanceof List<?> content) {
            for (Object value : content) {
                if (value instanceof Map<?, ?> result && "tool_result".equals(result.get("type"))) {
                    String callId = text(result.get("id"));
                    if (callId != null) {
                        var classification = ActivityToolRegistryService.classify(defaultText(result.get("name")));
                        var status = resultStatus(text(result.get("state")));
                        var reason = status == ActivityStatus.WAITING
                                ? (classification.type() == ActivityType.EXTERNAL_ACTION ? ActivityWaitReason.APPROVAL : ActivityWaitReason.INPUT) : null;
                        save(scope, target, event, new ActivityProjectionChange("tool:" + callId,
                                classification, status, reason));
                    }
                }
            }
        } else if (id != null && List.of("tool/requested", "tool/dispatch", "tool/suspended", "tool/decision").contains(event.type())) {
            ActivityStatus status = switch (event.type()) {
                case "tool/requested" -> ActivityStatus.QUEUED;
                case "tool/dispatch" -> ActivityStatus.RUNNING;
                case "tool/suspended" -> ActivityStatus.WAITING;
                default -> "denied".equals(text(data.get("decision"))) ? ActivityStatus.CANCELLED : null;
            };
            if (status != null) {
                var classification = ActivityToolRegistryService.classify(name == null ? "" : name);
                var reason = status == ActivityStatus.WAITING
                        ? (classification.type() == ActivityType.EXTERNAL_ACTION ? ActivityWaitReason.APPROVAL : ActivityWaitReason.INPUT) : null;
                save(scope, target, event, new ActivityProjectionChange("tool:" + id, classification, status, reason));
            }
        }
    }

    /**
     * 业务记录与已完成活动在同一事务提交；仅记录已发生的边界，不补造持续时间。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(ExecutionScopeDO scope, ActivityBusinessFact fact) {
        completed(scope, activities.masterBinding(scope.userId(), scope.scopeId()), fact);
    }

    /** 指定实际触发业务事件的绑定，重试记录归属到对应的执行节点。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(ExecutionScopeDO scope, String bindingId, ActivityBusinessFact fact) {
        if (bindingId == null) {
            throw new IllegalStateException("活动来源缺少执行绑定");
        }
        String key = bindingId + ":business:" + ContentDigests.sha256(fact.type() + ":" + fact.key());
        if (activities.find(scope.userId(), bindingId, key) != null) {
            return;
        }
        var classification = switch (fact.type()) {
            case CONTEXT_PREPARE -> new ActivityClassification(fact.type(), ActivityGroup.PREPARATION, "准备对话资料");
            case WORKER_DISPATCH -> new ActivityClassification(fact.type(), ActivityGroup.PLANNING, "任务已接手");
            case ANSWER_PUBLISH -> new ActivityClassification(fact.type(), ActivityGroup.DELIVERY, "交付回答");
            case AUTO_RETRY_SCHEDULED -> new ActivityClassification(fact.type(), ActivityGroup.EXECUTION, "已安排自动重试");
            case USER_RETRY_SCHEDULED -> new ActivityClassification(fact.type(), ActivityGroup.EXECUTION, "已受理你的重试请求");
            default -> throw new IllegalArgumentException("不支持的业务活动边界");
        };
        var job = workers.findByBinding(scope.userId(), bindingId);
        var task = scope.kind().equals("TASK") ? tasks.findOwned(scope.userId(), scope.ownerId()) : null;
        var row = new ExecutionActivityDO(UUID.randomUUID().toString(), scope.userId(), scope.scopeId(), scope.conversationId(),
                activities.sourceMessage(scope.userId(), scope.scopeId()), scope.kind().equals("RESPONSE") ? scope.ownerId() : null,
                task == null ? null : task.taskId(), job == null ? null : job.jobId(), bindingId, null, null, key,
                job != null ? job.planVersion() : task == null ? 0 : task.planVersion(), classification.type(), classification.group(),
                ActivityStatus.SUCCEEDED, null, classification.summary(), null, LocalDateTime.now(ZoneOffset.UTC), 1, 0);
        activities.insert(row);
        publish(scope, row, fact.key());
    }

    private void save(ExecutionScopeDO scope, ProjectionTargetDO target, NativeEventView event, ActivityProjectionChange change) {
        String key = target.bindingId() + ":" + ContentDigests.sha256(event.turnId() + ":" + change.source());
        var previous = activities.find(scope.userId(), target.bindingId(), key);
        if (previous != null) {
            transition(scope, previous, event, change.status(), change.waitReason());
            return;
        }
        var job = workers.findByBinding(scope.userId(), target.bindingId());
        var task = scope.kind().equals("TASK") ? tasks.findOwned(scope.userId(), scope.ownerId()) : null;
        LocalDateTime now = timestamp(event);
        var row = new ExecutionActivityDO(UUID.randomUUID().toString(), scope.userId(), scope.scopeId(), scope.conversationId(),
                activities.sourceMessage(scope.userId(), scope.scopeId()), scope.kind().equals("RESPONSE") ? scope.ownerId() : null,
                task == null ? null : task.taskId(), job == null ? null : job.jobId(), target.bindingId(), event.runId(), event.turnId(), key,
                job != null ? job.planVersion() : task == null ? 0 : task.planVersion(), change.classification().type(),
                change.classification().group(), change.status(), change.waitReason(), change.classification().summary(),
                change.status() == ActivityStatus.RUNNING ? now : null, terminal(change.status()) ? now : null, 1, event.sequence());
        activities.insert(row);
        publish(scope, row, event.eventId());
    }

    private void transition(ExecutionScopeDO scope, ExecutionActivityDO row, NativeEventView event,
                            ActivityStatus status, ActivityWaitReason waitReason) {
        if (row.nativeSequence() >= event.sequence() || terminal(row.status())) {
            return;
        }
        if (row.status() == status && row.waitReason() == waitReason) {
            return;
        }
        var changed = new ExecutionActivityDO(row.activityId(), row.userId(), row.scopeId(), row.conversationId(),
                row.sourceMessageId(), row.executionId(), row.taskId(), row.jobId(), row.bindingId(), row.runId(), row.turnId(),
                row.sourceKey(), row.planVersion(), row.type(), row.group(), status, waitReason, row.summary(),
                row.startedAt() == null && status == ActivityStatus.RUNNING ? timestamp(event) : row.startedAt(),
                terminal(status) || status == ActivityStatus.UNKNOWN ? timestamp(event) : null, row.version() + 1, event.sequence());
        if (activities.update(changed) != 1) {
            throw new IllegalStateException("活动更新与原生消费位置发生竞争");
        }
        publish(scope, changed, event.eventId());
    }

    private void closeRun(ExecutionScopeDO scope, ProjectionTargetDO target, NativeEventView event) {
        for (var row : activities.activeRun(scope.userId(), target.bindingId(), event.runId())) {
            if ("suspended".equals(event.outcome())) {
                var reason = row.type() == ActivityType.EXTERNAL_ACTION ? ActivityWaitReason.APPROVAL : ActivityWaitReason.INPUT;
                transition(scope, row, event, ActivityStatus.WAITING, reason);
            } else {
                ActivityStatus status = row.type() == ActivityType.EXTERNAL_ACTION && row.status() == ActivityStatus.RUNNING
                        ? ActivityStatus.UNKNOWN : "cancelled".equals(event.outcome()) ? ActivityStatus.CANCELLED : ActivityStatus.UNKNOWN;
                transition(scope, row, event, status, null);
            }
        }
    }

    private ActivityStatus resultStatus(String result) {
        if (result == null) {
            return ActivityStatus.UNKNOWN;
        }
        return switch (result.toLowerCase(java.util.Locale.ROOT)) {
            case "success", "completed" -> ActivityStatus.SUCCEEDED;
            case "error", "failed" -> ActivityStatus.FAILED;
            case "cancelled", "interrupted", "denied" -> ActivityStatus.CANCELLED;
            case "suspended", "pending" -> ActivityStatus.WAITING;
            default -> ActivityStatus.UNKNOWN;
        };
    }

    private boolean terminal(ActivityStatus status) {
        return status == ActivityStatus.SUCCEEDED || status == ActivityStatus.FAILED || status == ActivityStatus.CANCELLED;
    }

    private LocalDateTime timestamp(NativeEventView event) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(event.occurredAtMillis()), ZoneOffset.UTC);
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String defaultText(Object value) {
        String name = text(value);
        return name == null ? "" : name;
    }

    private void publish(ExecutionScopeDO scope, ExecutionActivityDO row, String eventId) {
        events.publish(scope, "activity:" + row.activityId() + ":" + eventId, "activity.updated",
                new ActivityUpdatedEvent(2, ExecutionActivityView.from(row)));
    }
}
