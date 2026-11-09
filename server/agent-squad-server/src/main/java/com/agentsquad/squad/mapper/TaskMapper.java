package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.TaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户目标 ，不将框架执行记录伪装成任务状态
 */
@Mapper
public interface TaskMapper {

    int insert(@Param("taskId") Long taskId, @Param("userId") Long userId,
               @Param("conversationId") Long conversationId, @Param("inputJson") String inputJson,
               @Param("budgetLimitMicrounits") long budgetLimitMicrounits);

    TaskPO lockOwnedRow(@Param("userId") Long userId, @Param("taskId") Long taskId);

    Long conversationIdOf(@Param("userId") Long userId, @Param("taskId") Long taskId);

    Long lockConversation(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    /**
     * 所有目标写入遵循会话→目标锁序，避免与按Scope预留预算的事务反向取锁。
     */
    default TaskPO lockOwned(Long userId, Long taskId) {
        Long conversationId = conversationIdOf(userId, taskId);
        if (conversationId == null) {
            return null;
        }
        lockConversation(userId, conversationId);
        return lockOwnedRow(userId, taskId);
    }

    TaskPO findOwned(@Param("userId") Long userId, @Param("taskId") Long taskId);

    String findInputJson(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int countStartedCommands(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int updateCancelling(@Param("userId") Long userId, @Param("taskId") Long taskId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("cancelledAt") LocalDateTime cancelledAt);

    int updateCancelled(@Param("userId") Long userId, @Param("taskId") Long taskId,
                        @Param("cancelledAt") LocalDateTime cancelledAt);

    int updateResuming(@Param("userId") Long userId, @Param("taskId") Long taskId,
                       @Param("expectedVersion") long expectedVersion);

    int blockRejectedResume(@Param("userId") Long userId, @Param("taskId") Long taskId,
                            @Param("reason") String reason);

    List<TaskPO> listByConversation(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                                    @Param("cursorId") Long cursorId, @Param("limit") int limit);
}
