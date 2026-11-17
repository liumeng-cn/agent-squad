package com.agentsquad.squad.common.enums;


/**
 * 头像固定配色
 * <p>
 * 跨用户服务与网关共用的有限值，不包含生成实现
 */
@lombok.Getter
public enum AvatarPalette {
    BLUE("蓝色"),
    TEAL("青绿色"),
    VIOLET("紫色"),
    AMBER("琥珀色"),
    ROSE("玫瑰色"),
    CYAN("青蓝色"),
    GREEN("绿色"),
    INDIGO("靛蓝色");

    /**
     * 中文含义
     */
    private final String desc;

    AvatarPalette(String desc) {
        this.desc = desc;
    }
}
