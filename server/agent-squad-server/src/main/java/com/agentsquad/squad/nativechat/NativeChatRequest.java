package com.agentsquad.squad.nativechat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 不建会话表：浏览器只把当前页面的文本上下文带回本次调用。 */
public record NativeChatRequest(
        @NotBlank @Size(max = 12000) String message,
        @Size(max = 30) List<HistoryItem> history) {
    public record HistoryItem(@NotBlank String role, @NotBlank @Size(max = 12000) String content) {}
}
