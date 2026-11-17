package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 聊天公开消息角色
 * <p>
 * 声明聊天模块公开状态及中文说明，与接口和前端取值保持一致。
 */
@Getter
public enum ChatMessageRole {
    USER("用户"),
    ASSISTANT("助手");

    /**
     * 中文含义。
     */
    private final String desc;

    ChatMessageRole(String desc) {
        this.desc = desc;
    }
}
