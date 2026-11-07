package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.presentation.domain.ReplyPreviewPhase;

/**
 * 可丢弃的普通回答正文预览
 * <p>
 * text为当前模型调用累计公开文本，phase区分推理中与正文输出，offset固定为零并按revision整段替换；
 * 无持久序号，不证明执行完成，也不包含推理正文本身。
 */
public record ReplyPreview(int schemaVersion, String executionId, String sourceMessageId,
                           String runId, String streamId, long revision, int offset,
                           String text, ReplyPreviewPhase phase, boolean replace, boolean preview) {
}
