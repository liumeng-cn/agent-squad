package com.agentsquad.squad.sandbox.domain;


/**
 * 服务端固定的沙箱执行身份。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxExecutionContext(String userId, String scopeId, String bindingId, String sessionId,
                                      String guardToken, String turnId, String toolCallId) {
}
