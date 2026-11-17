package com.agentsquad.squad.common.enums;


/**
 * 头像生成风格
 * <p>
 * 跨用户服务与网关共用的有限值，不包含生成实现
 */
@lombok.Getter
public enum AvatarStyle {
    GEOMETRIC_V1("第一版稳定几何头像");

    /**
     * 中文含义
     */
    private final String desc;

    AvatarStyle(String desc) {
        this.desc = desc;
    }
}
