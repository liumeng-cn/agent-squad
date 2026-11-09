package com.agentsquad.squad.po;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 预算预留的幂等与结算投影。
 *
 * 同一 usageKey 的类型、金额、请求摘要及定价版本在重试时必须保持一致。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BudgetReservationPO {
    /** MODEL/TOOL */
    private String kind;
    /** RESERVED/SETTLED/UNKNOWN */
    private String status;
    /** 预留整数微单位 */
    private long reservedUnits;
    /** 已结算整数微单位 */
    private long settledUnits;
    /** 模型请求使用的服务端Profile键 */
    private String modelProfileKey;
    /** 模型定价版本，结算时不得切换 */
    private String pricingVersion;
    /** 模型请求输入规范化摘要，不保存完整Prompt */
    private String requestHash;
    /** 输入token累计 */
    private long inputTokens;
    /** 输出token累计 */
    private long outputTokens;
}
