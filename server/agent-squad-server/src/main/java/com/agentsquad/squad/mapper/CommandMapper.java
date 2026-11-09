package com.agentsquad.squad.mapper;

import com.agentsquad.squad.po.CommandPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 持久命令和幂等收据 ；按归属、作用域与调用键定位原命令
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

    CommandPO findByKey(@Param("userId") String userId, @Param("scopeKey") String scopeKey,
                         @Param("idempotencyKey") String idempotencyKey);

    CommandPO findByKeyCurrent(@Param("userId") String userId, @Param("scopeKey") String scopeKey,
                         @Param("idempotencyKey") String idempotencyKey);

    CommandPO findOwned(@Param("userId") String userId, @Param("commandId") String commandId);

    int countSignalTurn(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("turnId") String turnId);

    int countPlanSignalTurn(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("turnId") String turnId, @Param("planVersion") long planVersion);

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
