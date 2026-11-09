package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 会话公开事实的数据库查询结果
 * <p>
 * 字段只取同用户、同会话的已提交事实，不包含原生内部推理或运行候选。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExecutionDO {
    /** 回复执行业务ID */
    private String executionId;
    /** 来源用户消息ID */
    private String sourceMessageId;
    /** 回复生命周期状态 */
    private String status;
    /** 业务乐观版本 */
    private long version;
    /** 是否已请求取消 */
    private boolean cancellationRequested;
    /** 授权助手回答消息ID */
    private String answerMessageId;
    /** 最近结构化故障ID */
    private String failureId;
}
