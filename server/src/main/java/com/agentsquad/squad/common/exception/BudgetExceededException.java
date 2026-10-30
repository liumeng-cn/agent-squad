package com.agentsquad.squad.common.exception;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

/**
 * 处理额度不足的明确业务异常。
 * <p>
 * 在真实请求前拒绝执行，为安全故障分类提供类型依据，不改变HTTP冲突语义。
 */
public class BudgetExceededException extends RuntimeBusinessException {
    public BudgetExceededException(String message) {
        super(RuntimeExceptionCode.CONFLICT, message);
    }
}
