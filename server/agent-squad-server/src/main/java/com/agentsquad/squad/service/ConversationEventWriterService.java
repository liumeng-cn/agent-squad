package com.agentsquad.squad.service;

import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.entity.ExecutionScopeDO;
import com.agentsquad.squad.mapper.MessageAcceptanceMapper;
import com.agentsquad.squad.mapper.ConversationMapper;
import com.agentsquad.squad.domain.entity.ConversationDO;
import com.agentsquad.squad.domain.model.ConversationCommittedSignal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 在事务中将执行结果写入会话事件
 * <p>
 * 调用方先持有会话锁；删除期间仍可结算运行事实，但不重新公开会话内容。
 */
@Service
public class ConversationEventWriterService {
    @Resource
    private ApplicationEventPublisher committedEvents;

    @Resource
    private ConversationMapper conversations;

    @Resource
    private MessageAcceptanceMapper messages;

    @Resource
    private ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ExecutionScopeDO scope, String key, String type, Object payload) {
        ConversationDO conversation = conversations.lockOwned(scope.getUserId(), scope.getConversationId());
        if (conversation == null || !"ACTIVE".equals(conversation.getStatus())
                || conversation.getGeneration() != scope.getConversationGeneration()) {
            return;
        }
        if (messages.reserveEvent(scope.getUserId(), scope.getConversationId()) != 1) {
            return;
        }
        messages.insertEvent(scope.getUserId(), scope.getConversationId(), scope.getConversationGeneration(),
                messages.eventSequence(scope.getUserId(), scope.getConversationId()), key, type,
                RuntimePayloads.toJson(json, payload), LocalDateTime.now(ZoneOffset.UTC));
        committedEvents.publishEvent(new ConversationCommittedSignal(scope.getUserId(), scope.getConversationId()));
    }
}
