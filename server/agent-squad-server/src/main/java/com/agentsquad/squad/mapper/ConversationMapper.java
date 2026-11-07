package com.agentsquad.squad.mapper;

import com.agentsquad.squad.domain.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话 ；任务受理时用行锁串行化同一会话
 */
@Mapper
public interface ConversationMapper {

    int insert(@Param("conversationId") String conversationId, @Param("userId") String userId,
               @Param("title") String title);

    ConversationDO lockOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    ConversationDO findOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationDO> listByOwner(@Param("userId") String userId, @Param("cursorId") Long cursorId,
                                      @Param("limit") int limit);

    int activateTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("taskId") String taskId);

    int clearActiveTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                        @Param("taskId") String taskId);

    int markDeleting(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("expectedVersion") long expectedVersion);

    int markDeleted(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationDO> findDeletable(@Param("limit") int limit);
}
