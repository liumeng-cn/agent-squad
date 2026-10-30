package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.presentation.domain.ExecutionFailureRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结构化故障与有界恢复次数的持久边界。
 * <p>
 * 调用方持有会话及所有者锁，次数和排队命令同事务提交，不因进程重启或更换回合清零。
 */
@Mapper
public interface ExecutionFailureMapper {
    ExecutionFailureRow latest(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("bindingId") String bindingId);

    List<ExecutionFailureRow> activeInConversation(@Param("userId") String userId, @Param("conversationId") String conversationId);

    String byKey(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("key") String key);

    int insert(@Param("userId") String userId, @Param("row") ExecutionFailureRow row, @Param("key") String key);

    int status(@Param("userId") String userId, @Param("failureId") String failureId, @Param("status") String status);

    int resolveBinding(@Param("userId") String userId, @Param("bindingId") String bindingId);

    int serviceAttempts(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int planAttempts(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int reserveServiceRetry(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("next") LocalDateTime next);

    LocalDateTime nextAttempt(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int invalidPlan(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int responseRetrying(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("next") LocalDateTime next);

    String predecessor(@Param("userId") String userId, @Param("executionId") String executionId);

    int serviceAttemptsView(@Param("userId") String userId, @Param("scopeId") String scopeId);

    String sourceMessage(@Param("userId") String userId, @Param("scopeId") String scopeId);

    boolean retryAllowed(@Param("userId") String userId, @Param("executionId") String executionId);

    int clearWorkerCandidate(@Param("userId") String userId, @Param("bindingId") String bindingId);

    int recoverTask(@Param("userId") String userId, @Param("taskId") String taskId);

    String latestMasterCommand(@Param("userId") String userId, @Param("bindingId") String bindingId);

    Long executionVersion(@Param("userId") String userId, @Param("executionId") String executionId);

    int linkRetry(@Param("userId") String userId, @Param("executionId") String executionId, @Param("previous") String previous);

    boolean userActionAvailable(@Param("userId") String userId, @Param("bindingId") String bindingId,
                                @Param("kind") String kind, @Param("now") LocalDateTime now);

    int approveRetry(@Param("userId") String userId, @Param("failureId") String failureId, @Param("attempt") int attempt);

    int failTask(@Param("userId") String userId, @Param("taskId") String taskId);

    int linkFailure(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("failureId") String failureId);
}
