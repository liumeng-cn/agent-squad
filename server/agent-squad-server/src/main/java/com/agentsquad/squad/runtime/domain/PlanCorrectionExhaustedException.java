package com.agentsquad.squad.runtime.domain;

/**
 * 无效计划纠正次数耗尽。
 * <p>
 * 阻止在原执行中继续消耗模型预算，不依赖异常字符串识别故障。
 */
public class PlanCorrectionExhaustedException extends RuntimeException {
    public PlanCorrectionExhaustedException() {
        super("计划纠正次数已用完");
    }
}
