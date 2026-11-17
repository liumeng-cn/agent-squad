package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.CommandPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 持久命令和幂等收据 ；按归属、作用域与调用键定位原命令
 */
@Mapper
public interface CommandMapper {

    int insertSubmit(@Param("commandId") Long commandId, @Param("userId") Long userId,
                     @Param("scopeKey") String scopeKey, @Param("idempotencyKey") String idempotencyKey,
                     @Param("payloadHash") String payloadHash, @Param("taskId") Long taskId,
                     @Param("bindingId") Long bindingId, @Param("payloadJson") String payloadJson,
                     @Param("dueAt") LocalDateTime dueAt);

    int insertControl(@Param("commandId") Long commandId, @Param("userId") Long userId,
                      @Param("scopeKey") String scopeKey, @Param("idempotencyKey") String idempotencyKey,
                      @Param("payloadHash") String payloadHash, @Param("taskId") Long taskId,
                      @Param("bindingId") Long bindingId, @Param("action") String action,
                      @Param("payloadJson") String payloadJson, @Param("dueAt") LocalDateTime dueAt);

    int cancelPendingByTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    CommandPO findByKey(@Param("userId") Long userId, @Param("scopeKey") String scopeKey,
                        @Param("idempotencyKey") String idempotencyKey);

    CommandPO findByKeyCurrent(@Param("userId") Long userId, @Param("scopeKey") String scopeKey,
                               @Param("idempotencyKey") String idempotencyKey);

    CommandPO findOwned(@Param("userId") Long userId, @Param("commandId") Long commandId);

    int countSignalTurn(@Param("userId") Long userId, @Param("taskId") Long taskId,
                        @Param("turnId") String turnId);

    int countPlanSignalTurn(@Param("userId") Long userId, @Param("taskId") Long taskId,
                            @Param("turnId") String turnId, @Param("planVersion") long planVersion);

    int markMasterTurnApplied(@Param("userId") Long userId, @Param("taskId") Long taskId,
                              @Param("turnId") String turnId);

    int countUnprocessedMasterCommands(@Param("userId") Long userId,
                                       @Param("taskId") Long taskId);

    Long findWorkerSubmitCommandId(@Param("userId") Long userId,
                                     @Param("bindingId") Long bindingId);

    int countWorkerResumes(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int countPendingWorkerResumes(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    Long findScopeId(@Param("userId") Long userId, @Param("commandId") Long commandId);
}
