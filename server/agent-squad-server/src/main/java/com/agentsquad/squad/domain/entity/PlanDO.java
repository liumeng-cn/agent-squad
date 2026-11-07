package com.agentsquad.squad.domain.entity;

/**
 * 协作计划的数据库记录
 * <p>
 * 计划头不可变；status 表示 ACTIVE 或被新版本 SUPERSEDED，signal_version 记录汇合推进水位。
 */
public record PlanDO(long id, String planId, String userId, String taskId, long planVersion,
                      String status, String logicalKey, String definitionJson,
                      long signalVersion, long version, long goalRevision) {
}
