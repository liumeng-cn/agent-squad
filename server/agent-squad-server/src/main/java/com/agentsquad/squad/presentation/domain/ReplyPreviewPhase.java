package com.agentsquad.squad.presentation.domain;

/**
 * 临时回答预览的当前阶段
 * <p>
 * 只表达模型在推理还是已开始输出公开正文，不代表完成，也不携带推理或工具内容。
 */
public enum ReplyPreviewPhase {
    THINKING,
    ANSWERING,
    ABORTED
}
