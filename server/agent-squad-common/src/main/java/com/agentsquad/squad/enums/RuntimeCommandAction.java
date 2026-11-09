package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 持久命令的动作类型。
 * <p>
 * SUBMIT 创建目标原生回合，MESSAGE_SUBMIT启动独立消息回复；
 * STOP 请求合作中断，RESUME 恢复既有回合；
 * RESPOND 把用户对 INPUT/APPROVAL 交互的答复回填为原生的外部执行结果；
 * DELETE_CONVERSATION 是平台持久意图，不由原生会话投递器执行。
 */
@Getter
public enum RuntimeCommandAction {
    SUBMIT(1, "SUBMIT", "提交目标"),
    MESSAGE_SUBMIT(2, "MESSAGE_SUBMIT", "提交消息"),
    MASTER_SIGNAL(3, "MASTER_SIGNAL", "主控信号"),
    TASK_DIRECTIVE(4, "TASK_DIRECTIVE", "目标修改"),
    STOP(5, "STOP", "停止"),
    RESUME(6, "RESUME", "恢复"),
    RESPOND(7, "RESPOND", "回填答复"),
    DELETE_CONVERSATION(8, "DELETE_CONVERSATION", "删除对话");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    RuntimeCommandAction(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
