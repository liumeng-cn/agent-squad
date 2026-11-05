package com.agentsquad.squad.model.domain;

/**
 * 用户可选的模型思考模式
 * <p>
 * 只有模型目录声明且协议适配器支持的模式才可受理，不把开关虚构为更多强度档位。
 */
public enum ReasoningMode {
    DEFAULT, OFF, AUTO, ON, LOW, MEDIUM, HIGH
}
