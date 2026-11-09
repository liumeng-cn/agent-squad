package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 会话头与一致水位的数据库查询结果
 * <p>
 * 字段只取同用户、同会话的已提交事实，不包含原生内部推理或运行候选。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHeaderDO {
    /** 对话业务ID */
    private String conversationId;
    /** 用户对话标题 */
    private String title;
    /** 标题来源[LEGACY,FALLBACK,AUTO_PENDING,AUTO,MANUAL] */
    private String titleSource;
    /** 标题独立乐观版本 */
    private long titleVersion;
    /** ACTIVE/DELETING/DELETED */
    private String status;
    /** 会话代数 */
    private long generation;
    /** 业务版本 */
    private long version;
    /** 未终结目标ID */
    private String activeTaskId;
    /** 最近事件序号 */
    private long eventSequence;
    /** 最后用户交流或助手交付时间 */
    private java.time.LocalDateTime lastActivityAt;
}
