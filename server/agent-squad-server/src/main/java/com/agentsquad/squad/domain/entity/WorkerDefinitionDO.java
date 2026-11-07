package com.agentsquad.squad.domain.entity;

import com.agentsquad.squad.common.enums.worker.WorkerDefinitionStatus;

/**
 * Worker 能力定义数据库记录
 * <p>
 * 定义正文发布后不可修改；状态和乐观版本可单独变更。
 */
public record WorkerDefinitionDO(long id, String definitionId, String workerKey,
                                  String workerVersion, WorkerDefinitionStatus status, long version,
                                  String definitionJson, String publishKey, String payloadHash,
                                  String lastStatusKey, String lastStatusActor, String lastStatusHash) {
}
