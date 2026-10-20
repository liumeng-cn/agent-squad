package com.agentsquad.squad.runtime.bridge;

/** AgentScope 原生提交的同步收据，执行结果仍由后续回收流程处理。 */
public record NativeSubmissionReceipt(String commandId, String turnId, String runId, String status) {
}
