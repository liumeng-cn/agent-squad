package com.agentsquad.squad.model.bo;

/**
 * AgentScope 原生提交的同步收据，执行结果仍由后续回收流程处理。
 */
public record NativeSubmissionReceiptBO(String commandId, String turnId, String runId, String status) {
}
