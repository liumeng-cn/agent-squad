package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.RuntimeAdmissionSlotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 持久原生运行占位的接口。
 * <p>
 * 全局及用户容量计数由同一事务锁定并更新；每个 Binding 保留唯一占位记录，
 * 运行结束后可复用记录，但必须保留原命令归属用于审计与恢复。
 */
@Mapper
public interface RuntimeAdmissionMapper {

    int ensureCounter(@Param("poolKey") String poolKey);

    Integer lockCounter(@Param("poolKey") String poolKey);

    RuntimeAdmissionSlotPO lockSlot(@Param("bindingId") Long bindingId);

    Long lockBindingCursor(@Param("userId") Long userId,
                           @Param("bindingId") Long bindingId);

    int createSlot(@Param("userId") Long userId,
                   @Param("bindingId") Long bindingId,
                   @Param("commandId") Long commandId,
                   @Param("action") String action,
                   @Param("claimCursor") long claimCursor);

    int activateSlot(@Param("userId") Long userId,
                     @Param("bindingId") Long bindingId,
                     @Param("commandId") Long commandId,
                     @Param("action") String action,
                     @Param("claimCursor") long claimCursor);

    int bindTurn(@Param("userId") Long userId,
                 @Param("bindingId") Long bindingId,
                 @Param("commandId") Long commandId,
                 @Param("turnId") String turnId);

    int bindRecoveryRun(@Param("userId") Long userId,
                        @Param("bindingId") Long bindingId,
                        @Param("runId") String runId,
                        @Param("eventSequence") long eventSequence);

    String findCommandTurn(@Param("userId") Long userId,
                           @Param("commandId") Long commandId);

    String findPriorTurn(@Param("userId") Long userId,
                         @Param("bindingId") Long bindingId);

    String findLastProjectedEndedTurn(@Param("userId") Long userId,
                                      @Param("bindingId") Long bindingId);

    List<String> findCounterMismatches(@Param("limit") int limit);

    int releaseSlot(@Param("userId") Long userId,
                    @Param("bindingId") Long bindingId,
                    @Param("commandId") Long commandId);

    int increment(@Param("poolKey") String poolKey);

    int decrement(@Param("poolKey") String poolKey);
}
