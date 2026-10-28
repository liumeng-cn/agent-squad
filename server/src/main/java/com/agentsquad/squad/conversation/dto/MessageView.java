package com.agentsquad.squad.conversation.dto;

import com.agentsquad.squad.artifact.ArtifactView;

import java.util.List;

/**
 * 统一消息正文视图。
 * <p>
 * 按会话序号排序并按 revision 替换；历史任务消息允许没有独立回复执行身份。
 */
public record MessageView(String messageId, long sequence, String role, String text, long revision,
                          boolean completed, String sourceMessageId, String executionId, String taskId,
                          String occurredAt, List<ArtifactView> artifacts) {
}
