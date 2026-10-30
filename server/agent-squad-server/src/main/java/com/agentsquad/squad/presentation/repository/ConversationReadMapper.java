package com.agentsquad.squad.presentation.repository;

import com.agentsquad.squad.presentation.domain.ConversationTaskState;
import com.agentsquad.squad.presentation.domain.ConversationHeader;
import com.agentsquad.squad.presentation.domain.ConversationMessageRow;
import com.agentsquad.squad.presentation.domain.ConversationExecution;
import com.agentsquad.squad.presentation.domain.ConversationEventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话读投影与任务交付消息的持久边界。
 * <p>
 * 查询始终按用户与会话限定，写入由持有会话锁的公开事件服务调用。
 */
@Mapper
public interface ConversationReadMapper {
    ConversationHeader header(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationMessageRow> messages(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("before") Long before, @Param("limit") int limit);

    List<ConversationExecution> activeExecutions(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationEventRow> events(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("generation") long generation, @Param("after") long after);

    Long firstEvent(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("generation") long generation);

    List<String> interactionIds(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<String> deliverableIds(@Param("userId") String userId, @Param("conversationId") String conversationId);

    String taskAnswer(@Param("userId") String userId, @Param("taskId") String taskId);

    String taskSource(@Param("userId") String userId, @Param("taskId") String taskId);

    int insertTaskAnswer(@Param("messageId") String messageId, @Param("userId") String userId, @Param("conversationId") String conversationId, @Param("sequence") long sequence, @Param("text") String text, @Param("revision") long revision, @Param("sourceMessageId") String sourceMessageId, @Param("taskId") String taskId, @Param("now") LocalDateTime now);

    int replaceTaskAnswer(@Param("userId") String userId, @Param("messageId") String messageId, @Param("text") String text, @Param("revision") long revision);

    List<ConversationHeader> conversations(@Param("userId") String userId, @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId, @Param("limit") int limit);

    long conversationId(@Param("userId") String userId, @Param("conversationId") String conversationId);

    int rename(@Param("userId") String userId, @Param("conversationId") String conversationId, @Param("title") String title, @Param("version") long version);

    List<String> uncertainOperationIds(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationExecution> pageExecutions(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                               @Param("first") long first, @Param("last") long last);

    List<ConversationTaskState> pageTasks(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                          @Param("first") long first, @Param("last") long last);
}
