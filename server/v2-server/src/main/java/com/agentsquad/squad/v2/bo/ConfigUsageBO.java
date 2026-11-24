package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigStatus;
import lombok.Data;

/**
 * 上游引用关系
 * <p>
 * 记录引用当前资源的下游配置（最新配置版本或 Master 当前生效发布），用于停用与删除前保护。
 */
@Data
public class ConfigUsageBO {
    /**
     * 下游资源业务ID
     */
    private Long ownerId;

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
     * 引用来源（如最新配置版本或生效发布序号）
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
