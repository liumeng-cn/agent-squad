package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.ConversationTaskStateBO;
import com.agentsquad.squad.model.bo.ConversationHeaderBO;
import com.agentsquad.squad.model.po.ConversationMessagePO;
import com.agentsquad.squad.model.bo.ConversationExecutionBO;
import com.agentsquad.squad.model.po.ConversationEventPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话读投影与任务交付消息的
 * <p>
 * 查询始终按用户与会话限定，写入由持有会话锁的公开事件服务调用。
 */
@Mapper
public interface ConversationReadMapper {
    ConversationHeaderBO header(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    List<ConversationMessagePO> messages(@Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("before") Long before, @Param("limit") int limit);

    ConversationExecutionBO execution(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                                      @Param("executionId") Long executionId);

    List<ConversationExecutionBO> activeExecutions(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    List<ConversationEventPO> events(@Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("generation") long generation, @Param("after") long after);

    Long firstEvent(@Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("generation") long generation);

    List<Long> interactionIds(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    List<Long> deliverableIds(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    Long taskAnswer(@Param("userId") Long userId, @Param("taskId") Long taskId);

    Long taskSource(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int insertTaskAnswer(@Param("messageId") Long messageId, @Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("sequence") long sequence, @Param("text") String text, @Param("revision") long revision, @Param("sourceMessageId") Long sourceMessageId, @Param("taskId") Long taskId, @Param("now") LocalDateTime now);

    int replaceTaskAnswer(@Param("userId") Long userId, @Param("messageId") Long messageId, @Param("text") String text, @Param("revision") long revision);

    List<ConversationHeaderBO> conversations(@Param("userId") Long userId, @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId, @Param("limit") int limit);

    long conversationId(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    int rename(@Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("title") String title, @Param("version") long version);

    List<Long> uncertainOperationIds(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    List<ConversationExecutionBO> pageExecutions(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                                                 @Param("first") long first, @Param("last") long last);

    List<ConversationTaskStateBO> pageTasks(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                                            @Param("first") long first, @Param("last") long last);
}
