package com.agentsquad.squad.mapper;

import com.agentsquad.squad.po.RuntimeAdmissionSlotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 持久原生运行占位的接口。
 *
 * 全局及用户容量计数由同一事务锁定并更新；每个 Binding 保留唯一占位记录，
 * 运行结束后可复用记录，但必须保留原命令归属用于审计与恢复。
 */
@Mapper
public interface RuntimeAdmissionMapper {

    int ensureCounter(@Param("poolKey") String poolKey);

    Integer lockCounter(@Param("poolKey") String poolKey);

    RuntimeAdmissionSlotPO lockSlot(@Param("bindingId") String bindingId);

    Long lockBindingCursor(@Param("userId") String userId,
                           @Param("bindingId") String bindingId);

    int createSlot(@Param("userId") String userId,
                   @Param("bindingId") String bindingId,
                   @Param("commandId") String commandId,
                   @Param("action") String action,
                   @Param("claimCursor") long claimCursor);

    int activateSlot(@Param("userId") String userId,
                     @Param("bindingId") String bindingId,
                     @Param("commandId") String commandId,
                     @Param("action") String action,
                     @Param("claimCursor") long claimCursor);

    int bindTurn(@Param("userId") String userId,
                 @Param("bindingId") String bindingId,
                 @Param("commandId") String commandId,
                 @Param("turnId") String turnId);

    int bindRecoveryRun(@Param("userId") String userId,
                        @Param("bindingId") String bindingId,
                        @Param("runId") String runId,
                        @Param("eventSequence") long eventSequence);

    String findCommandTurn(@Param("userId") String userId,
                           @Param("commandId") String commandId);

    String findPriorTurn(@Param("userId") String userId,
                         @Param("bindingId") String bindingId);

    String findLastProjectedEndedTurn(@Param("userId") String userId,
                                     @Param("bindingId") String bindingId);

    List<String> findCounterMismatches(@Param("limit") int limit);

    int releaseSlot(@Param("userId") String userId,
                    @Param("bindingId") String bindingId,
                    @Param("commandId") String commandId);

    int increment(@Param("poolKey") String poolKey);

    int decrement(@Param("poolKey") String poolKey);
}
