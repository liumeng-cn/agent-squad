package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * Master 发布预检视图
 * <p>
 * 展示本次发布即将定格的完整依赖版本清单及校验结果；前端确认时回传 dependencyDigest 防止发布前依赖漂移。
 */
@Data
public class AgentPublishPreviewVO {
    private String agentId;

    private String agentName;

    private Long revision;

    private Integer masterVersionNo;

    private Integer currentReleaseNo;

    private String dependencyDigest;

    private boolean publishable;

    private List<String> errors = List.of();

    private List<AgentPublishDependencyVO> dependencies = List.of();
}
