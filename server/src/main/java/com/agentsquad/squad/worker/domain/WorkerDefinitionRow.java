package com.agentsquad.squad.worker.domain;

/**
 * Worker 能力定义持久投影。
 * <p>
 * 定义正文发布后不可修改；状态和乐观版本可单独变更。
 */
public record WorkerDefinitionRow(long id, String definitionId, String workerKey,
                                  String workerVersion, String status, long version,
                                  String definitionJson, String publishKey, String payloadHash,
                                  String lastStatusKey, String lastStatusActor, String lastStatusHash) {
}
