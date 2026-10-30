package com.agentsquad.squad.sandbox.domain;


/**
 * 沙箱执行账本投影。
 * <p>
 * 仅表达受控执行边界的数据，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxRunRow(String executionId, String userId, String scopeId, String bindingId, String requestHash,
                            String status, String resultJson) {
}
