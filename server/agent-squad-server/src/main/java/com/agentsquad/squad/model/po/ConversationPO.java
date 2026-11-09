package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("sq_conversation")
public class ConversationPO {
    /** 存储主键ID */
    @TableId(type = IdType.AUTO)
    private long id;
    /** 对话业务ID */
    @TableField("conversation_id")
    private String conversationId;
    /** 资源归属用户ID */
    @TableField("user_id")
    private String userId;
    /** 用户对话标题 */
    private String title;
    /** ACTIVE/DELETING/DELETED */
    private String status;
    /** 未终结目标ID */
    @TableField("active_task_id")
    private String activeTaskId;
    /** 会话代数 */
    private long generation;
    /** 业务版本 */
    private long version;
}
