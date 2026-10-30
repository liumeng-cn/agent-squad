package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ConversationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话 SQL 边界；任务受理时用行锁串行化同一会话。
 */
@Mapper
public interface ConversationMapper {

    int insert(@Param("conversationId") String conversationId, @Param("userId") String userId,
               @Param("title") String title);

    ConversationRow lockOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    ConversationRow findOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationRow> listByOwner(@Param("userId") String userId, @Param("cursorId") Long cursorId,
                                      @Param("limit") int limit);

    int activateTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("taskId") String taskId);

    int clearActiveTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                        @Param("taskId") String taskId);

    int markDeleting(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("expectedVersion") long expectedVersion);

    int markDeleted(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationRow> findDeletable(@Param("limit") int limit);
}
