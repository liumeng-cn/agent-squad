package com.agentsquad.squad.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新对话请求。
 */
public record ConversationCreateRequest(@NotBlank @Size(max = 120) String title) {
}
