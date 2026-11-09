package com.agentsquad.squad.model.vo;


import java.util.List;

/**
 * 当前计划的子任务集合。
 * <p>
 * 节点数受计划上限约束，接口保留标准分页结构以供工作台统一消费。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class WorkerJobPageVO {

    @com.fasterxml.jackson.annotation.JsonProperty("items")
    private final List<WorkerJobVO> items;

    @com.fasterxml.jackson.annotation.JsonProperty("nextCursor")
    private final String nextCursor;

    @com.fasterxml.jackson.annotation.JsonProperty("acceptanceChecks")
    private final List<AcceptanceCheckVO> acceptanceChecks;

    @com.fasterxml.jackson.annotation.JsonProperty("planVersion")
    private final long planVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("currentPlanVersion")
    private final long currentPlanVersion;

    @com.fasterxml.jackson.annotation.JsonCreator
    public WorkerJobPageVO(
            @com.fasterxml.jackson.annotation.JsonProperty("items") List<WorkerJobVO> items,
            @com.fasterxml.jackson.annotation.JsonProperty("nextCursor") String nextCursor,
            @com.fasterxml.jackson.annotation.JsonProperty("acceptanceChecks") List<AcceptanceCheckVO> acceptanceChecks,
            @com.fasterxml.jackson.annotation.JsonProperty("planVersion") long planVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("currentPlanVersion") long currentPlanVersion) {
        this.items = items;
        this.nextCursor = nextCursor;
        this.acceptanceChecks = acceptanceChecks;
        this.planVersion = planVersion;
        this.currentPlanVersion = currentPlanVersion;
    }
}
