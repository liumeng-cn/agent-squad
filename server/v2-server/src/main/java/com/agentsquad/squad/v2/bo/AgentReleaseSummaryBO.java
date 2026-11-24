package com.agentsquad.squad.v2.bo;

import lombok.Data;

import java.time.Instant;

/**
 * Master 发布快照摘要
 */
@Data
public class AgentReleaseSummaryBO {
    private Long releaseId;

    private Integer releaseNo;

    private Long masterVersionId;

    private Integer masterVersionNo;

    private String masterName;

    private String masterDisplayAlias;

    private String dependencyDigest;

    private Instant publishedAt;

    private boolean current;
}
