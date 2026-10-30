package com.agentsquad.squad.presentation.repository;

import com.agentsquad.squad.presentation.domain.ConversationTaskState;
import com.agentsquad.squad.presentation.domain.ConversationHeader;
import com.agentsquad.squad.presentation.domain.ConversationMessageRow;
import com.agentsquad.squad.presentation.domain.ConversationExecution;
import com.agentsquad.squad.presentation.domain.ConversationEventRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话读投影与任务交付消息的持久边界。
 * <p>
 * 查询始终按用户与会话限定，写入由持有会话锁的公开事件服务调用。
 */
@Mapper
public interface ConversationReadMapper {
    @ConstructorArgs({
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "title_source", javaType = String.class),
            @Arg(column = "title_version", javaType = long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "generation", javaType = long.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "active_task_id", javaType = String.class),
            @Arg(column = "event_sequence", javaType = long.class),
            @Arg(column = "last_activity_at", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT conversation_id,
                   title,
                   title_source,
                   title_version,
                   status,
                   generation,
                   version,
                   active_task_id,
                   conversation_event_seq AS event_sequence,
                   COALESCE(last_activity_at, create_time) AS last_activity_at
            FROM sq_conversation
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
              AND status = 'ACTIVE'
              AND del_flag = '0'
            """)
    ConversationHeader header(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @ConstructorArgs({
            @Arg(column = "message_id", javaType = String.class),
            @Arg(column = "sequence", javaType = long.class),
            @Arg(column = "role", javaType = String.class),
            @Arg(column = "text", javaType = String.class),
            @Arg(column = "revision", javaType = long.class),
            @Arg(column = "completed", javaType = boolean.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "execution_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "occurred_at", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT message_id,
                   message_seq AS sequence,
                   role,
                   content AS text,
                   revision,
                   completed,
                   source_message_id,
                   execution_id,
                   task_id,
                   occurred_at
            FROM sq_message
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
              AND del_flag = '0'
              AND (#{before} IS NULL OR message_seq < #{before})
            ORDER BY message_seq DESC
            LIMIT #{limit}
            """)
    List<ConversationMessageRow> messages(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("before") Long before, @Param("limit") int limit);

    @ConstructorArgs({
            @Arg(column = "execution_id", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "cancelled", javaType = boolean.class),
            @Arg(column = "answer_message_id", javaType = String.class),
            @Arg(column = "failure_id", javaType = String.class)
    })
    @Select("""
            SELECT r.execution_id,
                   r.source_message_id,
                   r.status,
                   r.version,
                   (r.cancel_requested_at IS NOT NULL) AS cancelled,
                   r.answer_message_id,
                   r.failure_id
            FROM sq_response_execution r
            JOIN sq_execution_scope s ON s.user_id = r.user_id
                AND s.scope_id = r.scope_id
            JOIN sq_conversation c ON c.user_id = s.user_id
                AND c.conversation_id = s.conversation_id
                AND c.generation = s.conversation_generation
            WHERE r.user_id = #{userId}
              AND r.conversation_id = #{conversationId}
              AND r.status NOT IN ('SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')
              AND r.del_flag = '0'
            ORDER BY r.id
            """)
    List<ConversationExecution> activeExecutions(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @ConstructorArgs({
            @Arg(column = "generation", javaType = long.class),
            @Arg(column = "sequence", javaType = long.class),
            @Arg(column = "type", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "occurred_at", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT generation,
                   event_seq AS sequence,
                   event_type AS type,
                   payload_json,
                   occurred_at
            FROM sq_conversation_event
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
              AND generation = #{generation}
              AND event_seq > #{after}
            ORDER BY event_seq
            LIMIT 100
            """)
    List<ConversationEventRow> events(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("generation") long generation, @Param("after") long after);

    @Select("""
            SELECT MIN(event_seq)
            FROM sq_conversation_event
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
              AND generation = #{generation}
            """)
    Long firstEvent(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("generation") long generation);

    @Select("""
            SELECT i.interaction_id
            FROM sq_interaction i
            JOIN sq_execution_scope s ON s.user_id = i.user_id
                AND s.scope_id = i.scope_id
            JOIN sq_conversation c ON c.user_id = s.user_id
                AND c.conversation_id = s.conversation_id
                AND c.generation = s.conversation_generation
            WHERE i.user_id = #{userId}
              AND s.conversation_id = #{conversationId}
              AND i.status = 'OPEN'
              AND i.del_flag = '0'
            ORDER BY i.id
            """)
    List<String> interactionIds(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Select("""
            SELECT DISTINCT a.artifact_id
            FROM sq_scope_artifact a
            JOIN sq_execution_scope s ON s.user_id = a.user_id
                AND s.scope_id = a.scope_id
            JOIN sq_conversation c ON c.user_id = s.user_id
                AND c.conversation_id = s.conversation_id
                AND c.generation = s.conversation_generation
            WHERE a.user_id = #{userId}
              AND s.conversation_id = #{conversationId}
              AND a.del_flag = '0'
              AND NOT EXISTS (SELECT 1 FROM sq_message_artifact m
                  WHERE m.user_id = a.user_id AND m.artifact_id = a.artifact_id)
            """)
    List<String> deliverableIds(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Select("""
            SELECT message_id
            FROM sq_message
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND role = 'ASSISTANT'
              AND del_flag = '0'
            ORDER BY message_seq DESC
            LIMIT 1
            FOR UPDATE
            """)
    String taskAnswer(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT origin_message_id
            FROM sq_goal_task
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
            """)
    String taskSource(@Param("userId") String userId, @Param("taskId") String taskId);

    @Insert("""
            INSERT INTO sq_message (
                message_id,
                user_id,
                conversation_id,
                message_seq,
                role,
                content,
                revision,
                completed,
                source_message_id,
                task_id,
                occurred_at,
                creator_id,
                last_updater_id
            ) VALUES (
                #{messageId},
                #{userId},
                #{conversationId},
                #{sequence},
                'ASSISTANT',
                #{text},
                #{revision},
                TRUE,
                #{sourceMessageId},
                #{taskId},
                #{now},
                #{userId},
                #{userId}
            )
            """)
    int insertTaskAnswer(@Param("messageId") String messageId, @Param("userId") String userId, @Param("conversationId") String conversationId, @Param("sequence") long sequence, @Param("text") String text, @Param("revision") long revision, @Param("sourceMessageId") String sourceMessageId, @Param("taskId") String taskId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_message
            SET content    = #{text},
                revision   = #{revision},
                completed  = TRUE
            WHERE user_id = #{userId}
              AND message_id = #{messageId}
              AND revision <= #{revision}
            """)
    int replaceTaskAnswer(@Param("userId") String userId, @Param("messageId") String messageId, @Param("text") String text, @Param("revision") long revision);

    @ConstructorArgs({
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "title_source", javaType = String.class),
            @Arg(column = "title_version", javaType = long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "generation", javaType = long.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "active_task_id", javaType = String.class),
            @Arg(column = "event_sequence", javaType = long.class),
            @Arg(column = "last_activity_at", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT conversation_id,
                   title,
                   title_source,
                   title_version,
                   status,
                   generation,
                   version,
                   active_task_id,
                   conversation_event_seq AS event_sequence,
                   COALESCE(last_activity_at, create_time) AS last_activity_at
            FROM sq_conversation
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
              AND del_flag = '0'
              AND (#{beforeTime} IS NULL
                   OR COALESCE(last_activity_at, create_time) < #{beforeTime}
                   OR (COALESCE(last_activity_at, create_time) = #{beforeTime} AND id < #{beforeId}))
            ORDER BY COALESCE(last_activity_at, create_time) DESC, id DESC
            LIMIT #{limit}
            """)
    List<ConversationHeader> conversations(@Param("userId") String userId, @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId, @Param("limit") int limit);

    @Select("""
            SELECT id
            FROM sq_conversation
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
            """)
    long conversationId(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @Update("""
            UPDATE sq_conversation
            SET title         = #{title},
                title_source  = 'MANUAL',
                title_version = title_version + 1,
                version       = version + 1
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
              AND title_version = #{version}
              AND status = 'ACTIVE'
            """)
    int rename(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("title") String title, @Param("version") long version);

    @Select("""
            SELECT o.operation_id
            FROM sq_operation o
            JOIN sq_execution_scope s ON s.user_id = o.user_id
                AND s.scope_id = o.scope_id
            JOIN sq_conversation c ON c.user_id = s.user_id
                AND c.conversation_id = s.conversation_id
                AND c.generation = s.conversation_generation
            WHERE o.user_id = #{userId}
              AND s.conversation_id = #{conversationId}
              AND o.status IN ('UNKNOWN','RECONCILING')
              AND o.del_flag = '0'
            ORDER BY o.id
            """)
    List<String> uncertainOperationIds(@Param("userId") String userId, @Param("conversationId") String conversationId);

    @ConstructorArgs({
            @Arg(column = "execution_id", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "cancelled", javaType = boolean.class),
            @Arg(column = "answer_message_id", javaType = String.class),
            @Arg(column = "failure_id", javaType = String.class)
    })
    @Select("""
            SELECT r.execution_id,
                   r.source_message_id,
                   r.status,
                   r.version,
                   (r.cancel_requested_at IS NOT NULL) AS cancelled,
                   r.answer_message_id,
                   r.failure_id
            FROM sq_response_execution r
            JOIN sq_message m ON m.user_id = r.user_id
                AND m.message_id = r.source_message_id
            WHERE r.user_id = #{userId}
              AND r.conversation_id = #{conversationId}
              AND m.message_seq BETWEEN #{first} AND #{last}
              AND r.del_flag = '0'
            ORDER BY r.id
            """)
    List<ConversationExecution> pageExecutions(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                               @Param("first") long first, @Param("last") long last);

    @ConstructorArgs({
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class)
    })
    @Select("""
            SELECT t.task_id,
                   t.origin_message_id AS source_message_id,
                   t.status,
                   t.version
            FROM sq_goal_task t
            JOIN sq_message m ON m.user_id = t.user_id
                AND m.message_id = t.origin_message_id
            WHERE t.user_id = #{userId}
              AND t.conversation_id = #{conversationId}
              AND m.message_seq BETWEEN #{first} AND #{last}
              AND t.del_flag = '0'
            ORDER BY t.id
            """)
    List<ConversationTaskState> pageTasks(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                          @Param("first") long first, @Param("last") long last);
}
