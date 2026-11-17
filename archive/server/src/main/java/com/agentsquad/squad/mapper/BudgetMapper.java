package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.BudgetReservationPO;
import com.agentsquad.squad.model.bo.BudgetAccountBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 预算预留 ；usage_key 同键幂等，UNKNOWN 只能由核对流程出账
 */
@Mapper
public interface BudgetMapper {

    Long findScopeBySession(@Param("userId") Long userId,
                            @Param("sessionId") String sessionId);

    BudgetAccountBO lockAccount(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int ensureUser(@Param("userId") Long userId);

    String lockUser(@Param("userId") Long userId);

    List<Long> listUserConsumed(@Param("userId") Long userId);

    default long sumUserConsumed(Long userId) {
        return listUserConsumed(userId).stream().reduce(0L, Math::addExact);
    }

    int insert(@Param("reservationId") Long reservationId, @Param("userId") Long userId,
               @Param("scopeId") Long scopeId, @Param("usageKey") String usageKey,
               @Param("kind") String kind, @Param("reservedUnits") long reservedUnits,
               @Param("creatorName") String creatorName);

    int insertModel(@Param("reservationId") Long reservationId, @Param("userId") Long userId,
                    @Param("scopeId") Long scopeId, @Param("usageKey") String usageKey,
                    @Param("reservedUnits") long reservedUnits,
                    @Param("modelProfileKey") String modelProfileKey,
                    @Param("pricingVersion") String pricingVersion,
                    @Param("requestHash") String requestHash);

    BudgetReservationPO findReservation(@Param("userId") Long userId,
                                        @Param("scopeId") Long scopeId,
                                        @Param("usageKey") String usageKey);

    String findStatus(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                      @Param("usageKey") String usageKey);

    Long findReservedUnits(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                           @Param("usageKey") String usageKey);

    List<Long> listAccountConsumed(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    default long sumConsumedUnits(Long userId, Long scopeId) {
        return listAccountConsumed(userId, scopeId).stream().reduce(0L, Math::addExact);
    }

    int settle(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
               @Param("usageKey") String usageKey,
               @Param("settledUnits") long settledUnits);

    int settleModel(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                    @Param("usageKey") String usageKey,
                    @Param("settledUnits") long settledUnits,
                    @Param("inputTokens") long inputTokens,
                    @Param("outputTokens") long outputTokens);

    int markModelUnknown(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                         @Param("usageKey") String usageKey);

    int markUnknown(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                    @Param("usageKey") String usageKey);
}
