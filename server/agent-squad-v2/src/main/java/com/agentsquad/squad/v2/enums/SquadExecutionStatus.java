package com.agentsquad.squad.v2.enums;

/** V2 单次消息执行状态。 */
public enum SquadExecutionStatus {
    CREATED, RUNNING, COMPLETED, FAILED, CANCELLED;
    /** 判断执行是否已经进入终态。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}