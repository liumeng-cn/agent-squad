package com.agentsquad.squad.runtime.dto;

/** 归属受控的命令回执；原生状态只是提交瞬间快照，不代表运行终态。 */
public record CommandResponse(String commandId, String taskId, String status,
                              String nativeTurnId, String nativeRunId, String nativeStatus) {
}
