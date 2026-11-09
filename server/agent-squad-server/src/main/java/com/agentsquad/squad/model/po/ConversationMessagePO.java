package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("sq_message")
public class ConversationMessagePO {
    /**
     * 消息业务ID
     */
    private String messageId;
    /**
     * 消息序号
     */
    private long sequence;
    /**
     * 消息角色[USER,ASSISTANT]
     */
    private String role;
    /**
     * 消息正文
     */
    private String text;
    /**
     * 正文替换版本
     */
    private long revision;
    /**
     * 正文是否完成
     */
    private boolean completed;
    /**
     * 回复所关联的用户消息ID
     */
    private String sourceMessageId;
    /**
     * 回复执行ID；旧任务消息为空
     */
    private String executionId;
    /**
     * 可选关联目标ID
     */
    private String taskId;
    /**
     * 消息发生时间
     */
    private java.time.LocalDateTime occurredAt;
}
