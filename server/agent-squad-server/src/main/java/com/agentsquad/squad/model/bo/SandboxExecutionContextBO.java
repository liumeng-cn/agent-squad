package com.agentsquad.squad.model.bo;


/**
 * 服务端固定的沙箱执行身份。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxExecutionContextBO(String userId, String scopeId, String bindingId, String sessionId,
                                        String guardToken, String turnId, String toolCallId) {
}
