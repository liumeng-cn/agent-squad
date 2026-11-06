package com.agentsquad.squad.common.enums.worker;

/**
 * 计划必要条件的检查结论
 * <p>
 * 未知不等于检查通过；这里只接收结论，不替代产生证据的验收过程。
 */
public enum PlanAcceptanceVerdict {
    PASS,
    FAIL,
    UNKNOWN
}
