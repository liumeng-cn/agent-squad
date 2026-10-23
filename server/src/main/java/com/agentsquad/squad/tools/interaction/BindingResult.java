package com.agentsquad.squad.tools.interaction;

/**
 * 框架交互绑定结果。
 * <p>
 * 记录持久交互的身份和类型；未受控的挂起请求由调用方以 null 表示。
 */
public record BindingResult(String interactionId, String kind) {
}
