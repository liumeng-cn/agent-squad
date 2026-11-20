package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 对话标题来源
 * <p>
 * 区分默认标题与用户手动命名，改名后固定为手动来源，不再被自动命名覆盖。
 */
@Getter
public enum ConvTitleSource {
    AUTO("自动生成"),
    MANUAL("用户命名");

    /**
     * 中文含义
     */
    private final String desc;

    ConvTitleSource(String desc) {
        this.desc = desc;
    }
}
