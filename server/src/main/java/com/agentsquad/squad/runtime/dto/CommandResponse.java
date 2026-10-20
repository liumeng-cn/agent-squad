package com.agentsquad.squad.runtime.dto;

/** 归属受控的命令回执。 */
public record CommandResponse(String commandId, String taskId, String status) {
}
