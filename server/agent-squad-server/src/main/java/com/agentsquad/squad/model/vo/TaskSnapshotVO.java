package com.agentsquad.squad.model.vo;


import java.util.List;

/**
 * 任务详情页快照；聚合目标状态、原始目标文本、Worker 任务、待处理交互及当前答案游标。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class TaskSnapshotVO {

    @com.fasterxml.jackson.annotation.JsonProperty("task")
    private final TaskVO task;

    @com.fasterxml.jackson.annotation.JsonProperty("goalMessage")
    private final String goalMessage;

    @com.fasterxml.jackson.annotation.JsonProperty("jobs")
    private final List<WorkerJobVO> jobs;

    @com.fasterxml.jackson.annotation.JsonProperty("pending")
    private final List<InteractionVO> pending;

    @com.fasterxml.jackson.annotation.JsonProperty("answer")
    private final AnswerVO answer;

    @com.fasterxml.jackson.annotation.JsonProperty("cursor")
    private final String cursor;

    @com.fasterxml.jackson.annotation.JsonCreator
    public TaskSnapshotVO(
            @com.fasterxml.jackson.annotation.JsonProperty("task") TaskVO task,
            @com.fasterxml.jackson.annotation.JsonProperty("goalMessage") String goalMessage,
            @com.fasterxml.jackson.annotation.JsonProperty("jobs") List<WorkerJobVO> jobs,
            @com.fasterxml.jackson.annotation.JsonProperty("pending") List<InteractionVO> pending,
            @com.fasterxml.jackson.annotation.JsonProperty("answer") AnswerVO answer,
            @com.fasterxml.jackson.annotation.JsonProperty("cursor") String cursor) {
        this.task = task;
        this.goalMessage = goalMessage;
        this.jobs = jobs;
        this.pending = pending;
        this.answer = answer;
        this.cursor = cursor;
    }
}
