package com.agentsquad.squad.presentation.dto;

/**
 * 可丢弃的普通回答正文预览
 * <p>
 * text为当前模型调用累计公开文本，offset固定为零并按revision整段替换；无持久序号，不证明执行完成。
 */
public record ReplyPreview(int schemaVersion, String executionId, String sourceMessageId,
                           String runId, String streamId, long revision, int offset,
                           String text, boolean replace, boolean preview) {
}
