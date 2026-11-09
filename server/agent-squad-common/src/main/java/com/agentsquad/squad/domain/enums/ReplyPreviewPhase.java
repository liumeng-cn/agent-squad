package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 临时回答预览的当前阶段
 * <p>
 * 只表达模型在推理还是已开始输出公开正文，不代表完成，也不携带推理或工具内容。
 */
@Getter
public enum ReplyPreviewPhase {
    THINKING(1, "THINKING", "思考中"),
    ANSWERING(2, "ANSWERING", "回答中"),
    ABORTED(3, "ABORTED", "已中止");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ReplyPreviewPhase(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
