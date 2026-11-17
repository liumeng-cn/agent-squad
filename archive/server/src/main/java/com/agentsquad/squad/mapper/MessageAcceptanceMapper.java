package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.MessageSubmissionBO;
import com.agentsquad.squad.enums.ResponseCompletionProtocol;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 用户消息受理的事务数据写入
 * <p>
 * 会话行锁下分配消息与公开水位；命令唯一键裁决首发并发，不在此调用模型。
 */
@Mapper
public interface MessageAcceptanceMapper {

    MessageSubmissionBO findSubmission(@Param("userId") Long userId, @Param("scopeKey") String scopeKey,
                                       @Param("key") String key);

    int reserveMessage(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                       @Param("now") LocalDateTime now);

    long messageSequence(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    int insertMessage(@Param("messageId") Long messageId, @Param("userId") Long userId,
                      @Param("conversationId") Long conversationId, @Param("sequence") long sequence,
                      @Param("text") String text, @Param("executionId") Long executionId,
                      @Param("now") LocalDateTime now);

    int insertExecution(@Param("executionId") Long executionId, @Param("userId") Long userId,
                        @Param("conversationId") Long conversationId, @Param("messageId") Long messageId,
                        @Param("scopeId") Long scopeId, @Param("bindingId") Long bindingId,
                        @Param("completionProtocol") ResponseCompletionProtocol completionProtocol);

    int setModelChoice(@Param("userId") Long userId, @Param("executionId") Long executionId, @Param("choice") String choice);

    int markFallbackTitle(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    int reserveEvent(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    long eventSequence(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    int insertEvent(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                    @Param("generation") long generation, @Param("sequence") long sequence,
                    @Param("producerKey") String producerKey, @Param("type") String type,
                    @Param("payloadJson") String payloadJson, @Param("now") LocalDateTime now);
}
