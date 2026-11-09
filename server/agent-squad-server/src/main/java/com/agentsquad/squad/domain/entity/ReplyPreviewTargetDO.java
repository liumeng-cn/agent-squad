package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 普通回复公开文本的所属信息
 * <p>
 * 从已受理会话读取稳定身份，仅自然输出且仍可执行的回复允许创建临时预览。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ReplyPreviewTargetDO {
    /** 资源归属用户ID */
    private String userId;
    /** 所属会话ID */
    private String conversationId;
    /** 回复执行ID；旧任务消息为空 */
    private String executionId;
    /** 回复所关联的用户消息ID */
    private String sourceMessageId;
}
