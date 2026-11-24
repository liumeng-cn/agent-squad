package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConfigReadiness;
import lombok.Data;

import java.time.Instant;

/**
 * 配置版本摘要视图
 */
@Data
public class VersionSummaryVO {
    /**
     * 配置版本业务ID
     */
    private String versionId;

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
