package com.agentsquad.squad.tools.operation.repository;

import com.agentsquad.squad.domain.entity.OperationDO;
import com.agentsquad.squad.domain.entity.ToolExecutionContextDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部操作账本 ；按原生调用键与业务意图键双路幂等，状态推进全部带版本 CAS
 */
@Mapper
public interface OperationMapper {

    ToolExecutionContextDO lockToolContext(@Param("userId") String userId, @Param("sessionId") String sessionId);

    int insert(@Param("operationId") String operationId, @Param("userId") String userId,
               @Param("taskId") String taskId, @Param("jobId") String jobId,
               @Param("bindingId") String bindingId, @Param("frameworkTurnId") String frameworkTurnId,
               @Param("toolCallId") String toolCallId, @Param("toolKey") String toolKey,
               @Param("toolVersion") String toolVersion,
               @Param("businessIntentKey") String businessIntentKey, @Param("effect") String effect,
               @Param("connectionId") String connectionId,
               @Param("connectionRevision") long connectionRevision,
               @Param("authVersion") long authVersion, @Param("requestHash") String requestHash,
               @Param("argumentsJson") String argumentsJson, @Param("status") String status,
               @Param("creatorName") String creatorName, @Param("budgetUsageKey") String budgetUsageKey);

    OperationDO findByNativeCall(@Param("userId") String userId,
                                  @Param("bindingId") String bindingId,
                                  @Param("frameworkTurnId") String frameworkTurnId,
                                  @Param("toolCallId") String toolCallId);

    OperationDO findByBusinessIntent(@Param("userId") String userId,
                                      @Param("scopeId") String scopeId,
                                      @Param("toolKey") String toolKey,
                                      @Param("businessIntentKey") String businessIntentKey);

    OperationDO findOwned(@Param("userId") String userId,
                           @Param("operationId") String operationId);

    List<OperationDO> listByTask(@Param("userId") String userId, @Param("taskId") String taskId,
                                  @Param("cursorId") Long cursorId, @Param("limit") int limit);

    Long cursorIdOf(@Param("userId") String userId, @Param("operationId") String operationId);

    int markWaitingApproval(@Param("userId") String userId,
                            @Param("operationId") String operationId,
                            @Param("expectedVersion") long expectedVersion);

    int markInFlight(@Param("userId") String userId, @Param("operationId") String operationId,
                     @Param("expectedVersion") long expectedVersion, @Param("owner") String owner,
                     @Param("fence") long fence,
                     @Param("leaseUntil") LocalDateTime leaseUntil,
                     @Param("externalKey") String externalKey);

    int completeInFlight(@Param("userId") String userId,
                         @Param("operationId") String operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("owner") String owner, @Param("fence") long fence,
                         @Param("status") String status,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") String updaterId);

    int markUnknown(@Param("userId") String userId, @Param("operationId") String operationId,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("updaterId") String updaterId);

    List<OperationDO> findExpiredInFlight(@Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    int reconcileOutcome(@Param("userId") String userId,
                         @Param("operationId") String operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("status") String status,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") String updaterId);

    int recordUnresolved(@Param("userId") String userId,
                         @Param("operationId") String operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") String updaterId);

    int cancelWaitingApproval(@Param("userId") String userId,
                              @Param("operationId") String operationId,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("resultJson") String resultJson,
                              @Param("updaterId") String updaterId);

    List<String> findUncertainInConversation(@Param("userId") String userId, @Param("scopeId") String scopeId);
    int cancelUnstartedByScope(@Param("userId") String userId, @Param("scopeId") String scopeId);
    java.util.List<OperationDO> lockUnstartedInScope(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
