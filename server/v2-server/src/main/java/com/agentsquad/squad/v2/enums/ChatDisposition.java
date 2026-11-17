package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 聊天输入归属
 * <p>
 * 声明聊天模块公开状态及中文说明，与接口和前端取值保持一致。
 */
@Getter
public enum ChatDisposition {
    NEW_TURN("开启下一轮"),
    STEERING("补充当前轮");

    /**
     * 中文含义。
     */
    private final String desc;

    ChatDisposition(String desc) {
        this.desc = desc;
    }
}
