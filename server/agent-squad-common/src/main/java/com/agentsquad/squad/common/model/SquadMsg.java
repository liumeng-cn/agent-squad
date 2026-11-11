package com.agentsquad.squad.common.model;

import com.agentsquad.squad.enums.SquadEventCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Squad SSE 统一事件信封。
 *
 * <p>eventType 原样保留 AgentScope AgentEventType 规范名称；平台自有事件使用独立命名空间。
 * nativeExecution 与 nativeSource 保留原生执行身份和事件来源，payload 保留原生事件字段。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SquadMsg {

    /** SSE 协议版本。 */
    private String protocolVersion;

    /** 原生事件 ID；平台事件使用平台生成的唯一 ID。 */
    private String eventId;

    /** 当前会话事件序号，用于持久化、补发和去重。 */
    private Long sequence;

    /** 原生事件名原样透传；平台事件使用 squad.* 前缀。 */
    private String eventType;

    /** 前端展示分类，不替代 eventType。 */
    private SquadEventCategory category;

    /** 所属会话 ID。 */
    private String conversationId;

    /** 所属用户请求轮次 ID。 */
    private String turnId;

    /** Agent 展示身份；无法从原生事件确定时为空，不推测。 */
    private SquadAgentRef agent;

    /** AgentScope 原生执行身份。 */
    private SquadExecutionRef nativeExecution;

    /** AgentScope 原生 source 路径；根 Agent 通常为空。 */
    private String nativeSource;

    /** 平台补充的事件关联信息。 */
    private SquadEventRelation relation;

    /** 原生事件完整字段或平台事件专属数据。 */
    private Map<String, Object> payload;

    /** 事件发生时间。 */
    private Instant timestamp;
}
