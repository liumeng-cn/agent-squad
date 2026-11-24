package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.time.Instant;

/**
 * Master 发布快照摘要视图
 */
@Data
public class AgentReleaseSummaryVO {
    private String releaseId;

    private Integer releaseNo;

    private String masterVersionId;

    private Integer masterVersionNo;

    private String masterName;

    private String masterDisplayAlias;

    private String dependencyDigest;

    private Instant publishedAt;

    private boolean current;
}
