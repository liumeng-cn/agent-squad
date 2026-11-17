package com.agentsquad.squad.v2.enums;


/**
 * 会话标题来源
 * <p>
 * v2会话列表有限值，运行投影不能替代原生执行事实
 */
@lombok.Getter
public enum SessionTitleSource {
    AUTO("自动生成"),
    MANUAL("用户命名");

    /**
     * 中文含义
     */
    private final String desc;

    SessionTitleSource(String desc) {
        this.desc = desc;
    }
}
