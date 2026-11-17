package com.agentsquad.squad.model.vo;

/**
 * 任务当前答案视图；D1 直接回答阶段为占位，内容由后续 Bridge 写入。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AnswerVO {

    @com.fasterxml.jackson.annotation.JsonProperty("revision")
    private final long revision;

    @com.fasterxml.jackson.annotation.JsonProperty("text")
    private final String text;

    @com.fasterxml.jackson.annotation.JsonProperty("completed")
    private final boolean completed;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AnswerVO(
            @com.fasterxml.jackson.annotation.JsonProperty("revision") long revision,
            @com.fasterxml.jackson.annotation.JsonProperty("text") String text,
            @com.fasterxml.jackson.annotation.JsonProperty("completed") boolean completed) {
        this.revision = revision;
        this.text = text;
        this.completed = completed;
    }
}
