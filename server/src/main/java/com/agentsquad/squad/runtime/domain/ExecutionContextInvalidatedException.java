package com.agentsquad.squad.runtime.domain;

/**
 * 固定执行上下文失效。
 * <p>
 * 授权撤销或记忆变更后禁止继续使用旧原生会话；异常只携带安全说明，不包含已删除正文。
 */
public class ExecutionContextInvalidatedException extends IllegalStateException {
    public ExecutionContextInvalidatedException(Throwable cause) {
        super("技能授权或记忆已变化，当前处理已停止使用旧上下文，请重新发起。", cause);
    }
}
