package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.OperationPO;
import com.agentsquad.squad.model.bo.ToolExecutionContextBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部操作账本 ；按原生调用键与业务意图键双路幂等，状态推进全部带版本 CAS
 */
@Mapper
public interface OperationMapper {

    ToolExecutionContextBO lockToolContext(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    int insert(@Param("operationId") Long operationId, @Param("userId") Long userId,
               @Param("taskId") Long taskId, @Param("jobId") Long jobId,
               @Param("bindingId") Long bindingId, @Param("frameworkTurnId") String frameworkTurnId,
               @Param("toolCallId") String toolCallId, @Param("toolKey") String toolKey,
               @Param("toolVersion") String toolVersion,
               @Param("businessIntentKey") String businessIntentKey, @Param("effect") String effect,
               @Param("connectionId") Long connectionId,
               @Param("connectionRevision") long connectionRevision,
               @Param("authVersion") long authVersion, @Param("requestHash") String requestHash,
               @Param("argumentsJson") String argumentsJson, @Param("status") String status,
               @Param("creatorName") String creatorName, @Param("budgetUsageKey") String budgetUsageKey);

    OperationPO findByNativeCall(@Param("userId") Long userId,
                                 @Param("bindingId") Long bindingId,
                                 @Param("frameworkTurnId") String frameworkTurnId,
                                 @Param("toolCallId") String toolCallId);

    OperationPO findByBusinessIntent(@Param("userId") Long userId,
                                     @Param("scopeId") Long scopeId,
                                     @Param("toolKey") String toolKey,
                                     @Param("businessIntentKey") String businessIntentKey);

    OperationPO findOwned(@Param("userId") Long userId,
                          @Param("operationId") Long operationId);

    List<OperationPO> listByTask(@Param("userId") Long userId, @Param("taskId") Long taskId,
                                 @Param("cursorId") Long cursorId, @Param("limit") int limit);

    Long cursorIdOf(@Param("userId") Long userId, @Param("operationId") Long operationId);

    int markWaitingApproval(@Param("userId") Long userId,
                            @Param("operationId") Long operationId,
                            @Param("expectedVersion") long expectedVersion);

    int markInFlight(@Param("userId") Long userId, @Param("operationId") Long operationId,
                     @Param("expectedVersion") long expectedVersion, @Param("owner") String owner,
                     @Param("fence") long fence,
                     @Param("leaseUntil") LocalDateTime leaseUntil,
                     @Param("externalKey") String externalKey);

    int completeInFlight(@Param("userId") Long userId,
                         @Param("operationId") Long operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("owner") String owner, @Param("fence") long fence,
                         @Param("status") String status,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") Long updaterId);

    int markUnknown(@Param("userId") Long userId, @Param("operationId") Long operationId,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("updaterId") Long updaterId);

    List<OperationPO> findExpiredInFlight(@Param("now") LocalDateTime now,
                                          @Param("limit") int limit);

    int reconcileOutcome(@Param("userId") Long userId,
                         @Param("operationId") Long operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("status") String status,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") Long updaterId);

    int recordUnresolved(@Param("userId") Long userId,
                         @Param("operationId") Long operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") Long updaterId);

    int cancelWaitingApproval(@Param("userId") Long userId,
                              @Param("operationId") Long operationId,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("resultJson") String resultJson,
                              @Param("updaterId") Long updaterId);

    List<Long> findUncertainInConversation(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int cancelUnstartedByScope(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    java.util.List<OperationPO> lockUnstartedInScope(@Param("userId") Long userId, @Param("scopeId") Long scopeId);
}
