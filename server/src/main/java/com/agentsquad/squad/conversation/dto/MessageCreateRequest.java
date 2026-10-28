package com.agentsquad.squad.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户消息提交请求。
 * <p>
 * 只接受正文与就绪附件引用；用户、执行作用域和任务由服务端决定。
 */
public record MessageCreateRequest(@NotBlank @Size(max = 20000) String text,
                                   @Size(max = 20) List<@NotBlank String> artifactIds) {
}
