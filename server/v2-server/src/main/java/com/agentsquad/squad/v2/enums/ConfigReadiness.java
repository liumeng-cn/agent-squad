package com.agentsquad.squad.v2.enums;

/**
 * 配置版本就绪状态
 * <p>
 * 保存配置时不阻断未填完的必填项，由后端按资源规则计算就绪状态；仅 READY 且启用的资源可被下游关联或纳入发布。
 */
public enum ConfigReadiness {
    /**
     * 必填项完整且校验通过，可被下游关联
     */
    READY,

    /**
     * 存在未填写的必填项或关联不可用，仅可暂存，不可被下游关联
     */
    INCOMPLETE
}
