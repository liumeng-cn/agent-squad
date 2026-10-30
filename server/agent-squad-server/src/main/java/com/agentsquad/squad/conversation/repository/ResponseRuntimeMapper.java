package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ResponseRuntimeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回复生命周期、完成候选及公开消息的持久边界。
 * <p>
 * 调用方先持有会话和回复所有者锁，再维护候选、命令与消息，保证重投幂等。
 */
@Mapper
public interface ResponseRuntimeMapper {

    ResponseRuntimeRow lockByBinding(@Param("userId") String userId, @Param("bindingId") String bindingId);

    String bindingForExecution(@Param("userId") String userId, @Param("executionId") String executionId);

    int status(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("status") String status);

    int propose(@Param("userId") String userId, @Param("bindingId") String bindingId,
                @Param("turnId") String turnId, @Param("json") String json, @Param("hash") String hash);

    int finish(@Param("userId") String userId, @Param("bindingId") String bindingId,
               @Param("status") String status, @Param("answerId") String answerId);

    int correct(@Param("userId") String userId, @Param("bindingId") String bindingId);

    int recovering(@Param("userId") String userId, @Param("bindingId") String bindingId);

    int cancelling(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("now") LocalDateTime now);

    int cancelPending(@Param("userId") String userId, @Param("bindingId") String bindingId);

    List<Long> uncertainIds(@Param("userId") String userId, @Param("scopeId") String scopeId);

    List<Long> uncertainSandboxIds(@Param("userId") String userId, @Param("scopeId") String scopeId);

    default int uncertain(String userId, String scopeId) {
        return uncertainIds(userId, scopeId).size() + uncertainSandboxIds(userId, scopeId).size();
    }

    List<String> openKinds(@Param("userId") String userId, @Param("scopeId") String scopeId);

    default int openInteractions(String userId, String scopeId) {
        return openKinds(userId, scopeId).size();
    }

    int answer(@Param("messageId") String messageId, @Param("userId") String userId,
               @Param("conversationId") String conversationId, @Param("sequence") long sequence,
               @Param("sourceMessageId") String sourceMessageId, @Param("executionId") String executionId,
               @Param("text") String text, @Param("now") LocalDateTime now);

    String originalCommand(@Param("userId") String userId, @Param("bindingId") String bindingId);

    List<String> activeBindings(@Param("userId") String userId, @Param("conversationId") String conversationId);
}
