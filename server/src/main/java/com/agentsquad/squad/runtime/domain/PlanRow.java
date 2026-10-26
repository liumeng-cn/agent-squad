package com.agentsquad.squad.runtime.domain;

/**
 * 协作计划的持久投影。
 * <p>
 * 计划头不可变；status 表示 ACTIVE 或被新版本 SUPERSEDED，signal_version 记录汇合推进水位。
 */
public record PlanRow(long id, String planId, String userId, String taskId, long planVersion,
                      String status, String logicalKey, String definitionJson,
                      long signalVersion, long version) {
}
