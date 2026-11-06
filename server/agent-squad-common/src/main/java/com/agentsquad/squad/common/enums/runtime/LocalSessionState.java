package com.agentsquad.squad.common.enums.runtime;

/**
 * 本进程执行的取消观察状态
 * <p>
 * 只管理本机 SDK 资源，不替代持久任务与回复的业务状态。
 */
public enum LocalSessionState {
    ACTIVE,
    STOP_REQUESTED,
    FINISHED
}
