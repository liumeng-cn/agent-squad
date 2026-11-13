package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 消息 SSE 请求；新消息与断线补收共用一个接口。
 */
@Data
@NoArgsConstructor
public class SquadMessageRequestDTO {
    /**
     * 本轮用户输入；补收事件时为空。
     */
    @Size(max = 12000, message = "消息长度不能超过 12000 个字符")
    private String message;
    /**
     * 会话 Snowflake ID；使用字符串避免 JavaScript 丢失 Long 精度。
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "conversationId 必须是正整数")
    private String conversationId;
    /**
     * 单次执行 Snowflake ID；传入时表示恢复已有事件流。
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "executionId 必须是正整数")
    private String executionId;
    /**
     * 已接收的最后事件序号。
     */
    @Min(value = 0, message = "afterSequence 不能小于 0")
    private Long afterSequence;
}