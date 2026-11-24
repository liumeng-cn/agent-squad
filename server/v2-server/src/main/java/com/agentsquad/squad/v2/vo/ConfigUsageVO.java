package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

/**
 * 引用关系视图
 */
@Data
public class ConfigUsageVO {
    /**
     * 下游资源业务ID
     */
    private String ownerId;

    /**
     * 下游资源编码
     */
    private String ownerCode;

    /**
     * 下游资源名称
     */
    private String ownerName;

    /**
     * 下游资源启停状态
     */
    private ConfigStatus ownerStatus;

    /**
     * 引用对应的版本号或发布序号
     */
    private Integer versionNo;

    /**
     * 是否来自已发布生效快照
     */
    private boolean released;

    /**
     * 是否阻止停用或删除
     */
    private boolean active;
}
