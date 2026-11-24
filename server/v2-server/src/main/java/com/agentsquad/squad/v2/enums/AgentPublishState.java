package com.agentsquad.squad.v2.enums;

/**
 * Master Agent 发布状态
 * <p>
 * 用于 Master 列表展示当前发布快照与最新配置版本（含整条依赖链）是否一致；子 Agent 不具备发布状态。
 */
public enum AgentPublishState {
    /**
     * 尚未发布过任何快照
     */
    UNPUBLISHED,

    /**
     * 已发布且当前最新配置及依赖链与生效快照一致
     */
    PUBLISHED,

    /**
     * 已发布，但自身配置或任一上游依赖（Sub/Tool/MCP）已有更新的配置版本尚未发布
     */
    MODIFIED
}
