package com.agentsquad.squad.runtime.domain;

/**
 * 持久命令的动作类型。
 * <p>
 * SUBMIT 创建原生回合，STOP 请求合作中断，RESUME 恢复既有回合；
 * RESPOND 把用户对 INPUT/APPROVAL 交互的答复回填为原生的外部执行结果；
 * DELETE_CONVERSATION 是平台持久意图，不由原生会话投递器执行。
 */
public enum RuntimeCommandAction {
    SUBMIT,
    STOP,
    RESUME,
    RESPOND,
    DELETE_CONVERSATION
}
