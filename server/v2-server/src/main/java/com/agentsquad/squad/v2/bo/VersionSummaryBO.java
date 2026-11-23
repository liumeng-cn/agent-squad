package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

import java.time.Instant;

/**
 * 版本记录投影
 * <p>
 * 版本列表查询结果；current 对 Agent 为当前生效版本，对工具和 MCP 服务为最新发布版本。
 */
@Data
public class VersionSummaryBO {
    /**
     * 版本业务ID
     */
    private Long versionId;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 发布状态
     */
    private VersionStatus status;

    /**
     * 版本修订号
     */
    private Long revision;

    /**
     * 发布时间
     */
    private Instant publishedAt;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;

    /**
     * 是否当前生效或最新发布
     */
    private boolean current;
}
