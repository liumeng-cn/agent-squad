package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.MessageSubmissionRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户消息受理的事务持久边界。
 * <p>
 * 会话行锁下分配消息与公开水位；命令唯一键裁决首发并发，不在此调用模型。
 */
@Mapper
public interface MessageAcceptanceMapper {

    @ConstructorArgs({
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "execution_id", javaType = String.class),
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "event_seq", javaType = long.class)
    })
    @Select("""
            SELECT s.conversation_id, r.source_message_id, r.execution_id, c.command_id, s.scope_id,
                   c.payload_hash, r.status, s.conversation_generation, e.event_seq
            FROM sq_command c JOIN sq_execution_scope s ON s.user_id = c.user_id AND s.scope_id = c.scope_id
            JOIN sq_response_execution r ON r.user_id = s.user_id AND r.execution_id = s.owner_id AND r.scope_id = s.scope_id
            JOIN sq_conversation_event e ON e.user_id = c.user_id AND e.conversation_id = s.conversation_id
                AND e.producer_key = CONCAT('command:', c.command_id, ':execution.queued')
            WHERE c.user_id = #{userId} AND c.scope_key = #{scopeKey} AND c.idempotency_key = #{key}
              AND s.kind = 'RESPONSE'
            """)
    MessageSubmissionRow findSubmission(@Param("userId") String userId, @Param("scopeKey") String scopeKey,
                                        @Param("key") String key);

    @Update("""
            UPDATE sq_conversation SET message_seq = message_seq + 1, version = version + 1,
                                       last_activity_at = #{now}
            WHERE user_id = #{userId} AND conversation_id = #{conversationId} AND status = 'ACTIVE'
            """)
    int reserveMessage(@Param("userId") String userId, @Param("conversationId") String conversationId,
                       @Param("now") LocalDateTime now);

    @Select("SELECT message_seq FROM sq_conversation WHERE user_id = #{userId} AND conversation_id = #{conversationId}")
    long messageSequence(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Insert("""
            INSERT INTO sq_message (message_id, user_id, conversation_id, message_seq, role, content,
                                    completed, execution_id, occurred_at, creator_id, last_updater_id)
            VALUES (#{messageId}, #{userId}, #{conversationId}, #{sequence}, 'USER', #{text}, TRUE,
                    #{executionId}, #{now}, #{userId}, #{userId})
            """)
    int insertMessage(@Param("messageId") String messageId, @Param("userId") String userId,
                      @Param("conversationId") String conversationId, @Param("sequence") long sequence,
                      @Param("text") String text, @Param("executionId") String executionId,
                      @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO sq_response_execution (execution_id, user_id, conversation_id, source_message_id,
                                               scope_id, binding_id, status, creator_id, last_updater_id)
            VALUES (#{executionId}, #{userId}, #{conversationId}, #{messageId}, #{scopeId}, #{bindingId},
                    'QUEUED', #{userId}, #{userId})
            """)
    int insertExecution(@Param("executionId") String executionId, @Param("userId") String userId,
                        @Param("conversationId") String conversationId, @Param("messageId") String messageId,
                        @Param("scopeId") String scopeId, @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_conversation SET title_source = 'FALLBACK'
            WHERE user_id = #{userId} AND conversation_id = #{conversationId} AND title_source = 'LEGACY'
            """)
    int markFallbackTitle(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Update("""
            UPDATE sq_conversation SET conversation_event_seq = conversation_event_seq + 1
            WHERE user_id = #{userId} AND conversation_id = #{conversationId} AND status = 'ACTIVE'
            """)
    int reserveEvent(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Select("SELECT conversation_event_seq FROM sq_conversation WHERE user_id = #{userId} AND conversation_id = #{conversationId}")
    long eventSequence(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Insert("""
            INSERT INTO sq_conversation_event (user_id, conversation_id, generation, event_seq,
                                               producer_key, event_type, payload_json, occurred_at,
                                               creator_id, last_updater_id)
            VALUES (#{userId}, #{conversationId}, #{generation}, #{sequence}, #{producerKey}, #{type},
                    #{payloadJson}, #{now}, #{userId}, #{userId})
            """)
    int insertEvent(@Param("userId") String userId, @Param("conversationId") String conversationId,
                    @Param("generation") long generation, @Param("sequence") long sequence,
                    @Param("producerKey") String producerKey, @Param("type") String type,
                    @Param("payloadJson") String payloadJson, @Param("now") LocalDateTime now);
}
