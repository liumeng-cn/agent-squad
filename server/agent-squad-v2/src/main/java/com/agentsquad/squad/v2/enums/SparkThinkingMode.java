package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 星火思考模式
 * <p>
 * 对应讯飞请求体 thinking.type，不使用提示词模拟模型思考内容。
 */
@Getter
public enum SparkThinkingMode {
    ENABLED("enabled", "开启思考"),
    DISABLED("disabled", "关闭思考"),
    AUTO("auto", "模型自行判断");

    /**
     * 讯飞协议取值。
     */
    private final String value;

    /**
     * 中文说明。
     */
    private final String desc;

    SparkThinkingMode(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
