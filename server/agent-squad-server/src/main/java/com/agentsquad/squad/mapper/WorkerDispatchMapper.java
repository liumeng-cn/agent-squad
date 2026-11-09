package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.WorkerLaunchBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * Worker Binding、业务 guard 和结果候选的
 */
@Mapper
public interface WorkerDispatchMapper {

    WorkerLaunchBO findByBinding(@Param("userId") Long userId,
                                 @Param("bindingId") Long bindingId);

    int claimGuard(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                   @Param("guardToken") String guardToken, @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    int renewGuard(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                   @Param("guardToken") String guardToken, @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    int saveCandidate(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                      @Param("guardToken") String guardToken, @Param("turnId") String turnId,
                      @Param("resultJson") String resultJson, @Param("now") LocalDateTime now);

    int finalizeSuccess(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                        @Param("turnId") String turnId, @Param("now") LocalDateTime now);

    int supersedeInFlight(@Param("userId") Long userId,
                          @Param("bindingId") Long bindingId);

    int requestCancellation(@Param("userId") Long userId, @Param("taskId") Long taskId,
                            @Param("now") LocalDateTime now);

    int markFailed(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int markRecoveryBlocked(@Param("userId") Long userId,
                            @Param("bindingId") Long bindingId);
}
