package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 用户可选的模型思考模式
 * <p>
 * 只有模型目录声明且协议适配器支持的模式才可受理，不把开关虚构为更多强度档位。
 */
@Getter
public enum ReasoningMode {
    DEFAULT(1, "DEFAULT", "默认"),
    OFF(2, "OFF", "关闭"),
    AUTO(3, "AUTO", "自动"),
    ON(4, "ON", "开启"),
    LOW(5, "LOW", "低"),
    MEDIUM(6, "MEDIUM", "中"),
    HIGH(7, "HIGH", "高");

    /**
     * 编码
     */
    private final int code;
    /**
     * 英文名
     */
    private final String name;
    /**
     * 中文描述
     */
    private final String desc;

    ReasoningMode(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
