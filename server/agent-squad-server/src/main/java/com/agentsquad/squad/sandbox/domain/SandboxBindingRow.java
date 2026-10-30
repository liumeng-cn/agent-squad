package com.agentsquad.squad.sandbox.domain;


/**
 * 原生会话绑定的持久投影。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxBindingRow(String userId, String scopeId, String bindingId, String sessionId, String role) {
}
