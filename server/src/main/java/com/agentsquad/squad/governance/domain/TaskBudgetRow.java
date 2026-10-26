package com.agentsquad.squad.governance.domain;

/**
 * 父目标预算锁定投影。
 *
 * 预留事务读取并锁定同一目标的固定 Profile 与微单位上限，避免并发请求各自通过余额检查。
 */
public record TaskBudgetRow(String profileKey, long limitMicrounits) {
}
