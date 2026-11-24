package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import lombok.Data;

import java.time.Instant;

/**
 * 配置版本摘要
 * <p>
 * 展示 MCP、工具和 Agent 每次保存产生的历史配置版本。
 */
@Data
public class VersionSummaryBO {
    /**
     * 配置版本业务ID
     */
    private Long versionId;

    /**
     * 配置版本号
     */
    private Integer versionNo;

    /**
     * 版本就绪状态
     */
    private ConfigReadiness readiness;

    /**
     * 保存时间
     */
    private Instant lastUpdateTime;
}
