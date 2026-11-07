package com.agentsquad.squad.domain.entity;


/**
 * 沙箱执行账本投影。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
public record SandboxRunRow(String executionId, String userId, String scopeId, String bindingId, String requestHash,
                            String status, String resultJson) {
}
