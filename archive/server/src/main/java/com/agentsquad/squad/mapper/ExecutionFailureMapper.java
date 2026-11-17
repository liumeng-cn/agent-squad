package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.ExecutionFailurePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结构化故障与有界恢复次数的
 * <p>
 * 调用方持有会话及所有者锁，次数和排队命令同事务提交，不因进程重启或更换回合清零。
 */
@Mapper
public interface ExecutionFailureMapper {
    ExecutionFailurePO latest(@Param("userId") Long userId, @Param("scopeId") Long scopeId, @Param("bindingId") Long bindingId);

    List<ExecutionFailurePO> activeInConversation(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    List<Long> activeResponseFailures(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                                        @Param("generation") long generation, @Param("exceptScopeId") Long exceptScopeId);

    Long byKey(@Param("userId") Long userId, @Param("scopeId") Long scopeId, @Param("key") String key);

    int countActiveByCode(@Param("userId") Long userId, @Param("scopeId") Long scopeId, @Param("code") String code);

    int insert(@Param("userId") Long userId, @Param("row") ExecutionFailurePO row, @Param("key") String key);

    int status(@Param("userId") Long userId, @Param("failureId") Long failureId, @Param("status") String status);

    int resolveBinding(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int serviceAttempts(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int planAttempts(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int reserveServiceRetry(@Param("userId") Long userId, @Param("scopeId") Long scopeId, @Param("next") LocalDateTime next);

    LocalDateTime nextAttempt(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int invalidPlan(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int responseRetrying(@Param("userId") Long userId, @Param("bindingId") Long bindingId, @Param("next") LocalDateTime next);

    Long predecessor(@Param("userId") Long userId, @Param("executionId") Long executionId);

    int serviceAttemptsView(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    Long sourceMessage(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    boolean retryAllowed(@Param("userId") Long userId, @Param("executionId") Long executionId);

    int clearWorkerCandidate(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int recoverTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    Long latestMasterCommand(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    Long executionVersion(@Param("userId") Long userId, @Param("executionId") Long executionId);

    int linkRetry(@Param("userId") Long userId, @Param("executionId") Long executionId, @Param("previous") Long previous);

    boolean userActionAvailable(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                                @Param("kind") String kind, @Param("now") LocalDateTime now);

    int approveRetry(@Param("userId") Long userId, @Param("failureId") Long failureId, @Param("attempt") int attempt);

    int failTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int linkFailure(@Param("userId") Long userId, @Param("bindingId") Long bindingId, @Param("failureId") Long failureId);
}
