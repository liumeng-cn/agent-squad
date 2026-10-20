package com.agentsquad.squad.runtime.dto;

/**
 * 平台受理收据；QUEUED 表示数据库提交成功，不代表模型已启动。
 */
public record TaskReceipt(String taskId, String commandId, String status) {
}
