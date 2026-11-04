package com.agentsquad.squad.integration.agentscope.journal;

/**
 * 已提交原生收据的适配状态
 * <p>
 * 只映射SDK运行事实，UNKNOWN不推断为成功；不替代回复、任务或Job的业务状态。
 */
enum NativeTaskState {
    QUEUED("queued"), RUNNING("running"), COMPLETED("completed"), SUSPENDED("suspended"),
    FAILED("failed"), INTERRUPTED("interrupted"), CANCELLED("cancelled"), REJECTED("rejected"), UNKNOWN("unknown");

    private final String nativeValue;

    NativeTaskState(String nativeValue) {
        this.nativeValue = nativeValue;
    }

    String nativeValue() {
        return nativeValue;
    }

    static NativeTaskState from(Object value) {
        for (NativeTaskState state : values()) {
            if (state.nativeValue.equals(value)) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
