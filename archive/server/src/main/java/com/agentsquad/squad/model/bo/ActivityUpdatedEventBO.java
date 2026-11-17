package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.model.vo.ExecutionActivityVO;

/**
 * 执行活动公开增量
 * <p>
 * 与活动及原生消费水位在同一事务提交，页面按活动版本直接替换，不触发全会话刷新。
 */
public record ActivityUpdatedEventBO(int schemaVersion, ExecutionActivityVO activity) {
}
