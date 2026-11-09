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

    int insert(@Param("taskId") String taskId, @Param("userId") String userId,
               @Param("conversationId") String conversationId, @Param("inputJson") String inputJson,
               @Param("budgetLimitMicrounits") long budgetLimitMicrounits);

    TaskPO lockOwnedRow(@Param("userId") String userId, @Param("taskId") String taskId);

    String conversationIdOf(@Param("userId") String userId, @Param("taskId") String taskId);

    String lockConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);

    /**
     * 所有目标写入遵循会话→目标锁序，避免与按Scope预留预算的事务反向取锁。
     */
    default TaskPO lockOwned(String userId, String taskId) {
        String conversationId = conversationIdOf(userId, taskId);
        if (conversationId == null) {
            return null;
        }
        lockConversation(userId, conversationId);
        return lockOwnedRow(userId, taskId);
    }

    TaskPO findOwned(@Param("userId") String userId, @Param("taskId") String taskId);

    String findInputJson(@Param("userId") String userId, @Param("taskId") String taskId);

    int countStartedCommands(@Param("userId") String userId, @Param("taskId") String taskId);

    int updateCancelling(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("cancelledAt") LocalDateTime cancelledAt);

    int updateCancelled(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("cancelledAt") LocalDateTime cancelledAt);

    int updateResuming(@Param("userId") String userId, @Param("taskId") String taskId,
                       @Param("expectedVersion") long expectedVersion);

    int blockRejectedResume(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("reason") String reason);

    List<TaskPO> listByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                     @Param("cursorId") Long cursorId, @Param("limit") int limit);
}
