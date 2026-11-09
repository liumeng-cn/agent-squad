package com.agentsquad.squad.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 消息受理的持久幂等投影。
 * <p>
 * 收据身份与原始排队水位取自同一Command、回复执行和会话事件，不从客户端回填。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MessageSubmissionBO {
    /** 对话业务ID */
    private String conversationId;
    /** 消息业务标识 */
    private String messageId;
    /** 回复执行ID */
    private String executionId;
    /** 命令业务ID */
    private String commandId;
    /** 统一执行作用域ID */
    private String scopeId;
    /** 规范化请求SHA256 */
    private String payloadHash;
    /** PENDING/DELIVERING/DELIVERED/APPLIED/BLOCKED */
    private String status;
    /** 会话代际 */
    private long generation;
    /** 消息受理序号 */
    private long receiptSequence;
}
