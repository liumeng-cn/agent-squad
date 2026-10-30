package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.domain.ConversationRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 执行事实到会话事件的事务写入边界。
 * <p>
 * 调用方先持有会话锁；删除期间仍可结算运行事实，但不重新公开会话内容。
 */
@Service
public class ConversationEventWriter {
    @Resource
    private ConversationMapper conversations;

    @Resource
    private MessageAcceptanceMapper messages;

    @Resource
    private ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ExecutionScopeRow scope, String key, String type, Object payload) {
        ConversationRow conversation = conversations.lockOwned(scope.userId(), scope.conversationId());
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
    }
}
