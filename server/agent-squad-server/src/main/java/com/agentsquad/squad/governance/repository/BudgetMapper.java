package com.agentsquad.squad.governance.repository;

import com.agentsquad.squad.governance.domain.BudgetReservationRow;
import com.agentsquad.squad.governance.domain.BudgetAccountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 预算预留 SQL 边界；usage_key 同键幂等，UNKNOWN 只能由核对流程出账。
 */
@Mapper
public interface BudgetMapper {

    String findScopeBySession(@Param("userId") String userId,
                             @Param("sessionId") String sessionId);

    BudgetAccountRow lockAccount(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int ensureUser(@Param("userId") String userId);

    String lockUser(@Param("userId") String userId);

    List<Long> listUserConsumed(@Param("userId") String userId);

    default long sumUserConsumed(String userId) {
        return listUserConsumed(userId).stream().reduce(0L, Math::addExact);
    }

    int insert(@Param("reservationId") String reservationId, @Param("userId") String userId,
               @Param("scopeId") String scopeId, @Param("usageKey") String usageKey,
               @Param("kind") String kind, @Param("reservedUnits") long reservedUnits,
               @Param("creatorName") String creatorName);

    int insertModel(@Param("reservationId") String reservationId, @Param("userId") String userId,
                    @Param("scopeId") String scopeId, @Param("usageKey") String usageKey,
                    @Param("reservedUnits") long reservedUnits,
                    @Param("modelProfileKey") String modelProfileKey,
                    @Param("pricingVersion") String pricingVersion,
                    @Param("requestHash") String requestHash);

    BudgetReservationRow findReservation(@Param("userId") String userId,
                                         @Param("scopeId") String scopeId,
                                         @Param("usageKey") String usageKey);

    String findStatus(@Param("userId") String userId, @Param("scopeId") String scopeId,
                      @Param("usageKey") String usageKey);

    Long findReservedUnits(@Param("userId") String userId, @Param("scopeId") String scopeId,
                           @Param("usageKey") String usageKey);

    List<Long> listAccountConsumed(@Param("userId") String userId, @Param("scopeId") String scopeId);

    default long sumConsumedUnits(String userId, String scopeId) {
        return listAccountConsumed(userId, scopeId).stream().reduce(0L, Math::addExact);
    }

    int settle(@Param("userId") String userId, @Param("scopeId") String scopeId,
               @Param("usageKey") String usageKey,
               @Param("settledUnits") long settledUnits);

    int settleModel(@Param("userId") String userId, @Param("scopeId") String scopeId,
                    @Param("usageKey") String usageKey,
                    @Param("settledUnits") long settledUnits,
                    @Param("inputTokens") long inputTokens,
                    @Param("outputTokens") long outputTokens);

    int markModelUnknown(@Param("userId") String userId, @Param("scopeId") String scopeId,
                         @Param("usageKey") String usageKey);
    int markUnknown(@Param("userId") String userId, @Param("scopeId") String scopeId,
                    @Param("usageKey") String usageKey);
}
