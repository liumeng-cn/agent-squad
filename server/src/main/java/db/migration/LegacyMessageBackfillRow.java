package db.migration;

import java.time.LocalDateTime;

/**
 * 历史公开消息的迁移投影。
 * <p>
 * 仅携带已持久输入或答案，用稳定来源键排序与去重，不包含内部工具结果。
 */
record LegacyMessageBackfillRow(String userId, String conversationId, String taskId, long taskStorageId,
                                String messageId, String role, String sourceMessageId, String content,
                                long revision, boolean completed, LocalDateTime occurredAt, String legacyKey,
                                String deleted) {
}
