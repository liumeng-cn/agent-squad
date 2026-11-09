package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.ResponseRuntimeMapper;
import com.agentsquad.squad.model.bo.NativeEventViewBO;


import com.agentsquad.squad.enums.ResponseCompletionProtocol;
import com.agentsquad.squad.model.bo.ProjectionTargetBO;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionEventTypes;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 已收件原生事件到现有业务投影的适配
 * <p>
 * 复用现有授权、幂等、业务锁序与连续游标；加入消费事务，不另建第二套业务状态裁决。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NativeEventBusinessWriter implements RuntimeEventBusinessWriter {
    @Resource
    private NativeProjectionService projections;

    @Resource
    private NaturalReplyOutputReader naturalOutputs;

    @Resource
    private ResponseRuntimeMapper replies;

    @Override
    public void apply(ProjectionTargetBO target, SessionEvent event) {
        Map<String, Object> data = SessionEventTypes.BUILTIN.contains(event.type()) ? event.data() : Map.of();
        Object status = "run/end".equals(event.type()) ? data.get("status") : null;
        if (target.getTaskId() == null && "run/end".equals(event.type()) && "completed".equals(status)
                && replies.completionProtocol(target.getUserId(), target.getBindingId())
                == ResponseCompletionProtocol.NATURAL) {
            data = new java.util.LinkedHashMap<>(data);
            data.put("naturalOutput", naturalOutputs.read(target, event));
        }
        projections.accept(target, new NativeEventViewBO(event.seq(), event.eventId(), event.type(),
                event.executionRunId(), event.turnId(), status == null ? null : status.toString(),
                event.occurredAt(), data));
    }
}
