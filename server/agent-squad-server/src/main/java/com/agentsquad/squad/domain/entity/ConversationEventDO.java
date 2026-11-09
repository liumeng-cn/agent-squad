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
public class ConversationEventDO {
    /** 事件所属会话版本号 */
    private long generation;
    /** 事件序号 */
    private long sequence;
    /** 事件类型 */
    private String type;
    /** 脱敏公开事件载荷 */
    private String payloadJson;
    /** 发生时间 */
    private java.time.LocalDateTime occurredAt;
}
