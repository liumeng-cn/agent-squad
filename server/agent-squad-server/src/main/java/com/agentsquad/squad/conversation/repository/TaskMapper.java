package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.TaskRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户目标 SQL 边界，不将框架执行记录伪装成任务状态。
 */
@Mapper
public interface TaskMapper {

    int insert(@Param("taskId") String taskId, @Param("userId") String userId,
               @Param("conversationId") String conversationId, @Param("inputJson") String inputJson,
               @Param("budgetLimitMicrounits") long budgetLimitMicrounits);

    TaskRow lockOwnedRow(@Param("userId") String userId, @Param("taskId") String taskId);

    String conversationIdOf(@Param("userId") String userId, @Param("taskId") String taskId);

    String lockConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);

    /**
     * 所有目标写入遵循会话→目标锁序，避免与按Scope预留预算的事务反向取锁。
     */
    default TaskRow lockOwned(String userId, String taskId) {
        String conversationId = conversationIdOf(userId, taskId);
        if (conversationId == null) {
            return null;
        }
        lockConversation(userId, conversationId);
        return lockOwnedRow(userId, taskId);
    }

    TaskRow findOwned(@Param("userId") String userId, @Param("taskId") String taskId);

    String findInputJson(@Param("userId") String userId, @Param("taskId") String taskId);

    int updateCancelling(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("cancelledAt") LocalDateTime cancelledAt);

    int updateCancelled(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("cancelledAt") LocalDateTime cancelledAt);

    int updateResuming(@Param("userId") String userId, @Param("taskId") String taskId,
                       @Param("expectedVersion") long expectedVersion);

    int blockRejectedResume(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("reason") String reason);

    List<TaskRow> listByConversation(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                     @Param("cursorId") Long cursorId, @Param("limit") int limit);
}
