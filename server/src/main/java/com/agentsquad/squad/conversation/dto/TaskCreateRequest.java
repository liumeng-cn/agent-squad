package com.agentsquad.squad.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交目标请求；用户输入不会作为身份或框架 Session ID 使用。
 */
public record TaskCreateRequest(@NotBlank @Size(max = 20000) String message) {
}
