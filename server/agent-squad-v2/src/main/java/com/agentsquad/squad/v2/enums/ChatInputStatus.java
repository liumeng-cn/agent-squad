package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 补充要求处理状态
 * <p>
 * 声明聊天模块公开状态及中文说明，与接口和前端取值保持一致。
 */
@Getter
public enum ChatInputStatus {
    ACCEPTED("已收到，等待应用"),
    APPLIED("已应用"),
    UNAPPLIED("尚未应用，执行已结束");

    /**
     * 中文含义。
     */
    private final String desc;

    ChatInputStatus(String desc) {
        this.desc = desc;
    }
}
