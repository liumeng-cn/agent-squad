package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 执行账户预算锁定投影。
 *
 * 预留事务读取并锁定同一共享账户的固定 Profile 与微单位上限，避免并发请求各自通过余额检查。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAccountBO {
    /** 固定预算Profile键 */
    private String profileKey;
    /** 固定共享预算上限，整数微单位 */
    private long limitMicrounits;
}
