package com.agentsquad.squad.domain.response;

/**
 * 平台受理收据；QUEUED 表示数据库提交成功，不代表模型已启动。
 */
public record TaskReceiptResponse(String taskId, String commandId, String status) {
}
