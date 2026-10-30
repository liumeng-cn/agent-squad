package com.agentsquad.squad.conversation.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
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
    /**
     * 消息入口不接受调用方注入用户、任务或执行身份；未知字段按传输契约拒绝。
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("消息请求包含不支持的字段");
    }
}
