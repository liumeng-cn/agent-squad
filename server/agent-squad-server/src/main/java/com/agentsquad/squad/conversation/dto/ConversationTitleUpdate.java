package com.agentsquad.squad.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

/**
 * 手动修改会话标题的请求。
 * <p>
 * 独立标题版本避免执行状态变化导致误覆盖，晚到的自动建议不能覆盖已手动命名的主题。
 */
public record ConversationTitleUpdate(@NotBlank @Size(max = 200) String title,
                                      @jakarta.validation.constraints.NotNull @Min(0) Long expectedTitleVersion) {
}
