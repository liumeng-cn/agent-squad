package com.agentsquad.squad.common.model;

import com.agentsquad.squad.enums.SquadEventCategory;
import com.agentsquad.squad.enums.SquadEventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Squad 服务向客户端推送的统一事件消息。
 *
 * <p>该模型是 AgentScope 与前端之间的稳定传输协议，不暴露 AgentScope 内部对象。
 * payload 使用开放 Map 承载不同事件的专属数据；固定身份和关联关系使用独立模型。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SquadMsg {

    /**
     * 协议版本。
     */
    private String protocolVersion;

    /**
     * 事件唯一 ID。
     */
    private String eventId;

    /**
     * 当前 Turn 内递增的事件序号。
     */
    private Long sequence;

    /**
     * 事件类型。
     */
    private SquadEventType eventType;

    /**
     * 事件展示分类。
     */
    private SquadEventCategory category;

    /**
     * 所属会话 ID。
     */
    private String conversationId;

    /**
     * 所属用户请求轮次 ID。
     */
    private String turnId;

    /**
     * 事件所属 Agent；Turn 级系统事件可以为空。
     */
    private SquadAgentRef agent;

    /**
     * 事件关联信息。
     */
    private SquadEventRelation relation;

    /**
     * 当前事件的专属数据。
     */
    private Map<String, Object> payload;

    /**
     * 事件发生时间。
     */
    private Instant timestamp;
}
