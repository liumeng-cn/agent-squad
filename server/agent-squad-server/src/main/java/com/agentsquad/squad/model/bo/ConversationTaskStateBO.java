package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 历史消息关联的工作状态。
 * <p>
 * 取消等终态作为状态事实显示，不伪造一条模型生成的回答。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTaskStateBO {
    /**
     * 目标任务ID
     */
    private String taskId;
    /**
     * 来源用户消息ID
     */
    private String sourceMessageId;
    /**
     * 活动状态
     */
    private String status;
    /**
     * 公开活动版本
     */
    private long version;
}
