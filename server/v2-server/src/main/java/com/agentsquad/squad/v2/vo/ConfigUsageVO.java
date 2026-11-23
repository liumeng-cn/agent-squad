package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

/**
 * 引用关系视图
 * <p>
 * 下游资源的某个版本引用了当前资源的某个版本；active 为生效引用，存在生效引用时当前资源不能停用。
 */
@Data
public class ConfigUsageVO {
    /**
     * 引用方业务ID
     */
    private String ownerId;

    /**
     * 引用方编码
     */
    private String ownerCode;

    /**
     * 引用方名称
     */
    private String ownerName;

    /**
     * 引用方启停状态
     */
    private ConfigStatus ownerStatus;

    /**
     * 引用方版本号
     */
    private Integer versionNo;

    /**
     * 引用方版本状态
     */
    private VersionStatus versionStatus;

    /**
     * 被引用的当前资源版本号
     */
    private Integer referencedVersionNo;

    /**
     * 是否生效引用
     */
    private boolean active;
}
