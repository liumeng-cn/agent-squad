package com.agentsquad.squad.agent;

import io.agentscope.core.agent.RuntimeContext;

/**
 * AgentScope 原生运行上下文的业务 ID 读取。
 * <p>
 * SDK 上下文只保存字符串，进入平台服务前统一解析为正数 Long；无效值按上下文损坏处理。
 */
public final class RuntimeContextIds {

    private RuntimeContextIds() {
    }

    public static Long userId(RuntimeContext context) {
        String value = context.getUserId();
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("用户标识必须为正数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("运行上下文用户标识无效", exception);
        }
    }
}
