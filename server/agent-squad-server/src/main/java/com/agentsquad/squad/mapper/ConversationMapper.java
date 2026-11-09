package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.ConversationPO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话 ；任务受理时用行锁串行化同一会话
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationPO> {

    int insertConversation(@Param("conversationId") String conversationId, @Param("userId") String userId,
                           @Param("title") String title);

    ConversationPO lockOwned(@Param("userId") String userId, @Param("conversationId") String conversationId);

    default ConversationPO findOwned(String userId, String conversationId) {
        return selectOne(new QueryWrapper<ConversationPO>()
                .eq("user_id", userId)
                .eq("conversation_id", conversationId));
    }

    List<ConversationPO> listByOwner(@Param("userId") String userId, @Param("cursorId") Long cursorId,
                                     @Param("limit") int limit);

    int activateTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("taskId") String taskId);

    int clearActiveTask(@Param("userId") String userId, @Param("conversationId") String conversationId,
                        @Param("taskId") String taskId);

    int markDeleting(@Param("userId") String userId, @Param("conversationId") String conversationId,
                     @Param("expectedVersion") long expectedVersion);

    int markDeleted(@Param("userId") String userId, @Param("conversationId") String conversationId);

    List<ConversationPO> findDeletable(@Param("limit") int limit);
}
