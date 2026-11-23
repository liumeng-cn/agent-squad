package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * 配置版本状态
 * <p>
 * 每个资源最多一个草稿；草稿可反复保存，发布后内容冻结，下游按版本ID固定引用。
 */
@Getter
public enum VersionStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布");

    private final String desc;

    VersionStatus(String desc) {
        this.desc = desc;
    }
}
