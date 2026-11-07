package com.agentsquad.squad.mapper;

import com.agentsquad.squad.domain.entity.MessageSubmissionDO;
import com.agentsquad.squad.domain.enums.ResponseCompletionProtocol;
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

    MessageSubmissionDO findSubmission(@Param("userId") String userId, @Param("scopeKey") String scopeKey,
                                        @Param("key") String key);

    int reserveMessage(@Param("userId") String userId, @Param("conversationId") String conversationId,
                       @Param("now") LocalDateTime now);

    long messageSequence(@Param("userId") String userId, @Param("conversationId") String conversationId);

    int insertMessage(@Param("messageId") String messageId, @Param("userId") String userId,
                      @Param("conversationId") String conversationId, @Param("sequence") long sequence,
                      @Param("text") String text, @Param("executionId") String executionId,
                      @Param("now") LocalDateTime now);

    int insertExecution(@Param("executionId") String executionId, @Param("userId") String userId,
                        @Param("conversationId") String conversationId, @Param("messageId") String messageId,
                        @Param("scopeId") String scopeId, @Param("bindingId") String bindingId,
                        @Param("completionProtocol") ResponseCompletionProtocol completionProtocol);

    int setModelChoice(@Param("userId") String userId, @Param("executionId") String executionId, @Param("choice") String choice);

    int markFallbackTitle(@Param("userId") String userId, @Param("conversationId") String conversationId);

    int reserveEvent(@Param("userId") String userId, @Param("conversationId") String conversationId);

    long eventSequence(@Param("userId") String userId, @Param("conversationId") String conversationId);

    int insertEvent(@Param("userId") String userId, @Param("conversationId") String conversationId,
                    @Param("generation") long generation, @Param("sequence") long sequence,
                    @Param("producerKey") String producerKey, @Param("type") String type,
                    @Param("payloadJson") String payloadJson, @Param("now") LocalDateTime now);
}
