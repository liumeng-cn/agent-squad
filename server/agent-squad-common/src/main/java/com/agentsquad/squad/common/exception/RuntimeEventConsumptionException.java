package com.agentsquad.squad.common.exception;

import com.agentsquad.squad.domain.enums.RuntimeEventFailureCode;

/**
 * 原生事件消费中断
 * <p>
 * 只携带安全错误分类和原生序号，供后台在业务事务回滚后单独保存失败记录。
 */
public class RuntimeEventConsumptionException extends IllegalStateException {
    private final RuntimeEventFailureCode code;
    private final long sequence;

    public RuntimeEventConsumptionException(RuntimeEventFailureCode code, long sequence, String message) {
        super(message);
        this.code = code;
        this.sequence = sequence;
    }

    public RuntimeEventFailureCode code() {
        return code;
    }

    public long sequence() {
        return sequence;
    }
}
