package com.agentsquad.squad.governance.domain;

/**
 * 预算预留的幂等与结算投影。
 *
 * 同一 usageKey 的类型、金额、请求摘要及定价版本在重试时必须保持一致。
 */
public record BudgetReservationRow(String kind, String status, long reservedUnits,
                                   long settledUnits, String modelProfileKey,
                                   String pricingVersion, String requestHash,
                                   long inputTokens, long outputTokens) {
}
