package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.domain.entity.WorkerLaunchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * Worker Binding、业务 guard 和结果候选的
 */
@Mapper
public interface WorkerDispatchMapper {

    WorkerLaunchRow findByBinding(@Param("userId") String userId,
                                  @Param("bindingId") String bindingId);

    int claimGuard(@Param("userId") String userId, @Param("bindingId") String bindingId,
                   @Param("guardToken") String guardToken, @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    int renewGuard(@Param("userId") String userId, @Param("bindingId") String bindingId,
                   @Param("guardToken") String guardToken, @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    int saveCandidate(@Param("userId") String userId, @Param("bindingId") String bindingId,
                      @Param("guardToken") String guardToken, @Param("turnId") String turnId,
                      @Param("resultJson") String resultJson, @Param("now") LocalDateTime now);

    int finalizeSuccess(@Param("userId") String userId, @Param("bindingId") String bindingId,
                        @Param("turnId") String turnId, @Param("now") LocalDateTime now);

    int supersedeInFlight(@Param("userId") String userId,
                          @Param("bindingId") String bindingId);

    int requestCancellation(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("now") LocalDateTime now);

    int markFailed(@Param("userId") String userId, @Param("bindingId") String bindingId);

    int markRecoveryBlocked(@Param("userId") String userId,
                            @Param("bindingId") String bindingId);
}
