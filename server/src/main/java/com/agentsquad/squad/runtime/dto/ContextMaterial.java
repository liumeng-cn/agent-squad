package com.agentsquad.squad.runtime.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 节点授权材料协议。
 * <p>
 * 用户约束、显式记忆和技能版本各自保留来源与版本；信任标签不代替运行授权校验。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextMaterial(String kind, String sourceId, String sourceVersion, String trust,
                              String content, String contentHash, Long authorizationRevision) {
    public ContextMaterial(String kind, String sourceId, String sourceVersion, String trust, String content) {
        this(kind, sourceId, sourceVersion, trust, content, null, null);
    }
}
