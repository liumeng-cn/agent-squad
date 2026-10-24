package com.agentsquad.squad.conversation.dto;

/**
 * 控制命令受理收据；状态为命令在平台命令表中的状态，不代表模型已执行。
 */
public record CommandReceipt(String commandId, String taskId, String status,
                             String targetFrameworkTurnId, String reason) {
}
