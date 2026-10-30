package com.agentsquad.squad.conversation.domain;

/**
 * 已提交消息的上下文查询投影。
 * <p>
 * 只含公开角色正文，附件由独立归属关系补充，不读取内部原生日志。
 */
public record ResponseHistoryRow(String messageId, long sequence, String role, String text, long revision) {
}
