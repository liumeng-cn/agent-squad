package com.agentsquad.squad.model.vo;

import java.util.List;
import java.util.Map;

/**
 * 操作详情与服务端核对权限。
 * <p>
 * 参数只包含白名单字段的脱敏摘要，不可作为可编辑的执行参数副本。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class OperationDetailVO {

    @com.fasterxml.jackson.annotation.JsonProperty("operation")
    private final OperationVO operation;

    @com.fasterxml.jackson.annotation.JsonProperty("argumentsPreview")
    private final Map<String, Object> argumentsPreview;

    @com.fasterxml.jackson.annotation.JsonProperty("resultSummary")
    private final String resultSummary;

    @com.fasterxml.jackson.annotation.JsonProperty("evidenceRefs")
    private final List<String> evidenceRefs;

    @com.fasterxml.jackson.annotation.JsonProperty("canReconcile")
    private final boolean canReconcile;

    @com.fasterxml.jackson.annotation.JsonCreator
    public OperationDetailVO(
            @com.fasterxml.jackson.annotation.JsonProperty("operation") OperationVO operation,
            @com.fasterxml.jackson.annotation.JsonProperty("argumentsPreview") Map<String, Object> argumentsPreview,
            @com.fasterxml.jackson.annotation.JsonProperty("resultSummary") String resultSummary,
            @com.fasterxml.jackson.annotation.JsonProperty("evidenceRefs") List<String> evidenceRefs,
            @com.fasterxml.jackson.annotation.JsonProperty("canReconcile") boolean canReconcile) {
        this.operation = operation;
        this.argumentsPreview = argumentsPreview;
        this.resultSummary = resultSummary;
        this.evidenceRefs = evidenceRefs;
        this.canReconcile = canReconcile;
    }
}
