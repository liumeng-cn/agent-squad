package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

/**
 * 引用关系投影
 * <p>
 * 下游版本引用当前资源版本的查询结果；active 表示生效引用，用于停用保护。
 */
@Data
public class ConfigUsageBO {
    /**
     * 引用方业务ID
     */
    private Long ownerId;

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
     * 被引用的版本号
     */
    private Integer referencedVersionNo;

    /**
     * 是否生效引用
     */
    private boolean active;
}
