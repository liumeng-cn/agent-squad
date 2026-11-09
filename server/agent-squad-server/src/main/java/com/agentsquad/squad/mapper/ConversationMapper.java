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

    int insertConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                           @Param("title") String title);

    ConversationPO lockOwned(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    default ConversationPO findOwned(Long userId, Long conversationId) {
        return selectOne(new QueryWrapper<ConversationPO>()
                .eq("user_id", userId)
                .eq("conversation_id", conversationId));
    }

    List<ConversationPO> listByOwner(@Param("userId") Long userId, @Param("cursorId") Long cursorId,
                                     @Param("limit") int limit);

    int activateTask(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                     @Param("taskId") Long taskId);

    int clearActiveTask(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                        @Param("taskId") Long taskId);

    int markDeleting(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                     @Param("expectedVersion") long expectedVersion);

    int markDeleted(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    List<ConversationPO> findDeletable(@Param("limit") int limit);
}
