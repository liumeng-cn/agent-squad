package com.agentsquad.squad.memory.domain;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

/**
 * 旧执行记忆上下文失效。
 * <p>
 * 交给运行层转为可诊断阻塞；不得把旧Session的快照重新标为有效或自动重发外部写入。
 */
public class MemoryContextInvalidatedException extends RuntimeBusinessException {
    public MemoryContextInvalidatedException() {
        super(RuntimeExceptionCode.CONFLICT, "记忆已被纠正、删除或过期，旧执行上下文已阻塞，请重新发起安全执行");
    }
}
