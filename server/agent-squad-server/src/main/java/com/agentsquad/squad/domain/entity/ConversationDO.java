package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 对话数据库查询结果，只承载数据库事实
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDO {
    /** 存储主键ID */
    private long id;
    /** 对话业务ID */
    private String conversationId;
    /** 资源归属用户ID */
    private String userId;
    /** 用户对话标题 */
    private String title;
    /** ACTIVE/DELETING/DELETED */
    private String status;
    /** 未终结目标ID */
    private String activeTaskId;
    /** 会话代数 */
    private long generation;
    /** 业务版本 */
    private long version;
}
