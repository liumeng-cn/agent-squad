package com.agentsquad.squad.vo;


import com.agentsquad.squad.bo.ConversationTaskStateBO;

import java.util.List;

/**
 * 会话一致前缀快照。
 * <p>
 * 包含分页消息、在途执行与对话内交互；cursor 属于会话，不能与任务游标混用。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class ConversationSnapshotVO {

    @com.fasterxml.jackson.annotation.JsonProperty("conversation")
    private final ConversationViewVO conversation;

    @com.fasterxml.jackson.annotation.JsonProperty("messages")
    private final List<MessageVO> messages;

    @com.fasterxml.jackson.annotation.JsonProperty("nextBeforeSeq")
    private final Long nextBeforeSeq;

    @com.fasterxml.jackson.annotation.JsonProperty("activeExecutions")
    private final List<ResponseExecutionVO> activeExecutions;

    @com.fasterxml.jackson.annotation.JsonProperty("activeTask")
    private final TaskVO activeTask;

    @com.fasterxml.jackson.annotation.JsonProperty("interactions")
    private final List<InteractionVO> interactions;

    @com.fasterxml.jackson.annotation.JsonProperty("deliverables")
    private final List<ArtifactVO> deliverables;

    @com.fasterxml.jackson.annotation.JsonProperty("failures")
    private final List<ExecutionFailureVO> failures;

    @com.fasterxml.jackson.annotation.JsonProperty("operationIds")
    private final List<String> operationIds;

    @com.fasterxml.jackson.annotation.JsonProperty("executions")
    private final List<ResponseExecutionVO> executions;

    @com.fasterxml.jackson.annotation.JsonProperty("taskStates")
    private final List<ConversationTaskStateBO> taskStates;

    @com.fasterxml.jackson.annotation.JsonProperty("directives")
    private final List<TaskDirectiveVO> directives;

    @com.fasterxml.jackson.annotation.JsonProperty("activities")
    private final List<ExecutionActivityVO> activities;

    @com.fasterxml.jackson.annotation.JsonProperty("cursor")
    private final String cursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ConversationSnapshotVO(
            @com.fasterxml.jackson.annotation.JsonProperty("conversation") ConversationViewVO conversation,
            @com.fasterxml.jackson.annotation.JsonProperty("messages") List<MessageVO> messages,
            @com.fasterxml.jackson.annotation.JsonProperty("nextBeforeSeq") Long nextBeforeSeq,
            @com.fasterxml.jackson.annotation.JsonProperty("activeExecutions") List<ResponseExecutionVO> activeExecutions,
            @com.fasterxml.jackson.annotation.JsonProperty("activeTask") TaskVO activeTask,
            @com.fasterxml.jackson.annotation.JsonProperty("interactions") List<InteractionVO> interactions,
            @com.fasterxml.jackson.annotation.JsonProperty("deliverables") List<ArtifactVO> deliverables,
            @com.fasterxml.jackson.annotation.JsonProperty("failures") List<ExecutionFailureVO> failures,
            @com.fasterxml.jackson.annotation.JsonProperty("operationIds") List<String> operationIds,
            @com.fasterxml.jackson.annotation.JsonProperty("executions") List<ResponseExecutionVO> executions,
            @com.fasterxml.jackson.annotation.JsonProperty("taskStates") List<ConversationTaskStateBO> taskStates,
            @com.fasterxml.jackson.annotation.JsonProperty("directives") List<TaskDirectiveVO> directives,
            @com.fasterxml.jackson.annotation.JsonProperty("activities") List<ExecutionActivityVO> activities,
            @com.fasterxml.jackson.annotation.JsonProperty("cursor") String cursor) {
        this.conversation = conversation;
        this.messages = messages;
        this.nextBeforeSeq = nextBeforeSeq;
        this.activeExecutions = activeExecutions;
        this.activeTask = activeTask;
        this.interactions = interactions;
        this.deliverables = deliverables;
        this.failures = failures;
        this.operationIds = operationIds;
        this.executions = executions;
        this.taskStates = taskStates;
        this.directives = directives;
        this.activities = activities;
        this.cursor = cursor;
    }
}
