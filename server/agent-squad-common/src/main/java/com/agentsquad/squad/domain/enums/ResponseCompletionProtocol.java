package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 回复完成协议
 * <p>
 * 旧在途回复保留候选协议，新回复依据可信自然输出完成，恢复时不随配置切换。
 */
@Getter
public enum ResponseCompletionProtocol {
    PROPOSAL(1, "PROPOSAL", "候选协议"),
    NATURAL(2, "NATURAL", "自然输出");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ResponseCompletionProtocol(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
