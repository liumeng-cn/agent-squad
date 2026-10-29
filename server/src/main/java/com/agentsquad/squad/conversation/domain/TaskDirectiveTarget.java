package com.agentsquad.squad.conversation.domain;

/**
 * 修订协调扫描身份。
 * <p>
 * 扫描仅传递用户和目标身份，详细内容在会话与目标锁下读取。
 */
public record TaskDirectiveTarget(String directiveId, String userId, String taskId) {
}
