package com.agentsquad.squad.service;

import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.entity.ExecutionScopeDO;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.domain.entity.ConversationDO;
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
public class ConversationEventWriter {
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
        ConversationDO conversation = conversations.lockOwned(scope.userId(), scope.conversationId());
        if (conversation == null || !"ACTIVE".equals(conversation.status())
                || conversation.generation() != scope.conversationGeneration()) {
            return;
        }
        if (messages.reserveEvent(scope.userId(), scope.conversationId()) != 1) {
            return;
        }
        messages.insertEvent(scope.userId(), scope.conversationId(), scope.conversationGeneration(),
                messages.eventSequence(scope.userId(), scope.conversationId()), key, type,
                RuntimePayloads.toJson(json, payload), LocalDateTime.now(ZoneOffset.UTC));
        committedEvents.publishEvent(new ConversationCommittedSignal(scope.userId(), scope.conversationId()));
    }
}
