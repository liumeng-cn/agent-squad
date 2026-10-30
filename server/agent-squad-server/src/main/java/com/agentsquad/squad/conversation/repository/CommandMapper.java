package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.CommandRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 持久命令和幂等收据 SQL 边界；按归属、作用域与调用键定位原命令。
 */
@Mapper
public interface CommandMapper {

    int insertSubmit(@Param("commandId") String commandId, @Param("userId") String userId,
                     @Param("scopeKey") String scopeKey, @Param("idempotencyKey") String idempotencyKey,
                     @Param("payloadHash") String payloadHash, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("payloadJson") String payloadJson,
                     @Param("dueAt") LocalDateTime dueAt);

    int insertControl(@Param("commandId") String commandId, @Param("userId") String userId,
                      @Param("scopeKey") String scopeKey, @Param("idempotencyKey") String idempotencyKey,
                      @Param("payloadHash") String payloadHash, @Param("taskId") String taskId,
                      @Param("bindingId") String bindingId, @Param("action") String action,
                      @Param("payloadJson") String payloadJson, @Param("dueAt") LocalDateTime dueAt);

    int cancelPendingByTask(@Param("userId") String userId, @Param("taskId") String taskId);

    CommandRow findByKey(@Param("userId") String userId, @Param("scopeKey") String scopeKey,
                         @Param("idempotencyKey") String idempotencyKey);

    CommandRow findByKeyCurrent(@Param("userId") String userId, @Param("scopeKey") String scopeKey,
                         @Param("idempotencyKey") String idempotencyKey);

    CommandRow findOwned(@Param("userId") String userId, @Param("commandId") String commandId);

    int countSignalTurn(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("turnId") String turnId);

    int markMasterTurnApplied(@Param("userId") String userId, @Param("taskId") String taskId,
                              @Param("turnId") String turnId);

    int countUnprocessedMasterCommands(@Param("userId") String userId,
                                        @Param("taskId") String taskId);

    String findWorkerSubmitCommandId(@Param("userId") String userId,
                                      @Param("bindingId") String bindingId);

    int countWorkerResumes(@Param("userId") String userId, @Param("bindingId") String bindingId);

    int countPendingWorkerResumes(@Param("userId") String userId, @Param("bindingId") String bindingId);

    String findScopeId(@Param("userId") String userId, @Param("commandId") String commandId);
}
