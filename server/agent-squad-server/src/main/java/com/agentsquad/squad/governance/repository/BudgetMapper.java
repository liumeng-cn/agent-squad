package com.agentsquad.squad.governance.repository;

import com.agentsquad.squad.governance.domain.BudgetReservationRow;
import com.agentsquad.squad.governance.domain.BudgetAccountRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 预算预留 SQL 边界；usage_key 同键幂等，UNKNOWN 只能由核对流程出账。
 */
@Mapper
public interface BudgetMapper {

    @Select("""
            SELECT scope_id FROM sq_runtime_binding
            WHERE user_id = #{userId} AND framework_session_id = #{sessionId}
            """)
    String findScopeBySession(@Param("userId") String userId,
                             @Param("sessionId") String sessionId);

    @ConstructorArgs({
            @Arg(column = "budget_profile_key", javaType = String.class),
            @Arg(column = "budget_limit_microunits", javaType = long.class)
    })
    @Select("""
            SELECT a.profile_key AS budget_profile_key, a.limit_microunits AS budget_limit_microunits
            FROM sq_budget_account a JOIN sq_execution_scope s ON s.user_id = a.user_id AND s.account_id = a.account_id
            WHERE s.user_id = #{userId} AND s.scope_id = #{scopeId} AND a.status = 'OPEN' FOR UPDATE
            """)
    BudgetAccountRow lockAccount(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Insert("INSERT INTO sq_user_budget (user_id) VALUES (#{userId}) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)")
    int ensureUser(@Param("userId") String userId);

    @Select("SELECT user_id FROM sq_user_budget WHERE user_id = #{userId} FOR UPDATE")
    String lockUser(@Param("userId") String userId);

    @Select("""
            SELECT CASE WHEN status = 'SETTLED' THEN settled_units ELSE reserved_units END
            FROM sq_budget_reservation WHERE user_id = #{userId} AND del_flag = '0' FOR UPDATE
            """)
    List<Long> listUserConsumed(@Param("userId") String userId);

    default long sumUserConsumed(String userId) {
        return listUserConsumed(userId).stream().reduce(0L, Math::addExact);
    }

    @Insert("""
            INSERT INTO sq_budget_reservation
                (reservation_id, user_id, task_id, scope_id, account_id, usage_key, kind, reserved_units,
                 status, creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{reservationId}, #{userId},
                 (SELECT CASE WHEN kind = 'TASK' THEN owner_id ELSE NULL END FROM sq_execution_scope WHERE user_id = #{userId} AND scope_id = #{scopeId}),
                 #{scopeId}, (SELECT account_id FROM sq_execution_scope WHERE user_id = #{userId} AND scope_id = #{scopeId}), #{usageKey}, #{kind}, #{reservedUnits},
                 'RESERVED', #{userId}, #{creatorName}, #{userId}, #{creatorName})
            """)
    int insert(@Param("reservationId") String reservationId, @Param("userId") String userId,
               @Param("scopeId") String scopeId, @Param("usageKey") String usageKey,
               @Param("kind") String kind, @Param("reservedUnits") long reservedUnits,
               @Param("creatorName") String creatorName);

    @Insert("""
            INSERT INTO sq_budget_reservation
                (reservation_id, user_id, task_id, scope_id, account_id, usage_key, kind, reserved_units,
                 model_profile_key, pricing_version, request_hash, status,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{reservationId}, #{userId},
                 (SELECT CASE WHEN kind = 'TASK' THEN owner_id ELSE NULL END FROM sq_execution_scope WHERE user_id = #{userId} AND scope_id = #{scopeId}),
                 #{scopeId}, (SELECT account_id FROM sq_execution_scope WHERE user_id = #{userId} AND scope_id = #{scopeId}), #{usageKey}, 'MODEL', #{reservedUnits},
                 #{modelProfileKey}, #{pricingVersion}, #{requestHash}, 'RESERVED',
                 #{userId}, '', #{userId}, '')
            """)
    int insertModel(@Param("reservationId") String reservationId, @Param("userId") String userId,
                    @Param("scopeId") String scopeId, @Param("usageKey") String usageKey,
                    @Param("reservedUnits") long reservedUnits,
                    @Param("modelProfileKey") String modelProfileKey,
                    @Param("pricingVersion") String pricingVersion,
                    @Param("requestHash") String requestHash);

    @ConstructorArgs({
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "reserved_units", javaType = long.class),
            @Arg(column = "settled_units", javaType = long.class),
            @Arg(column = "model_profile_key", javaType = String.class),
            @Arg(column = "pricing_version", javaType = String.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "input_tokens", javaType = long.class),
            @Arg(column = "output_tokens", javaType = long.class)
    })
    @Select("""
            SELECT kind, status, reserved_units, settled_units, model_profile_key,
                   pricing_version, request_hash, input_tokens, output_tokens
            FROM sq_budget_reservation
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND usage_key = #{usageKey} FOR UPDATE
            """)
    BudgetReservationRow findReservation(@Param("userId") String userId,
                                         @Param("scopeId") String scopeId,
                                         @Param("usageKey") String usageKey);

    @Select("""
            SELECT status FROM sq_budget_reservation
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND usage_key = #{usageKey} FOR UPDATE
            """)
    String findStatus(@Param("userId") String userId, @Param("scopeId") String scopeId,
                      @Param("usageKey") String usageKey);

    @Select("""
            SELECT reserved_units FROM sq_budget_reservation
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND usage_key = #{usageKey} FOR UPDATE
            """)
    Long findReservedUnits(@Param("userId") String userId, @Param("scopeId") String scopeId,
                           @Param("usageKey") String usageKey);

    @Select("""
            SELECT CASE WHEN r.status = 'SETTLED' THEN r.settled_units ELSE r.reserved_units END
            FROM sq_budget_reservation r JOIN sq_execution_scope s
                ON s.user_id = r.user_id AND s.account_id = r.account_id
            WHERE s.user_id = #{userId} AND s.scope_id = #{scopeId} AND r.del_flag = '0' FOR UPDATE
            """)
    List<Long> listAccountConsumed(@Param("userId") String userId, @Param("scopeId") String scopeId);

    default long sumConsumedUnits(String userId, String scopeId) {
        return listAccountConsumed(userId, scopeId).stream().reduce(0L, Math::addExact);
    }

    @Update("""
            UPDATE sq_budget_reservation
            SET status = 'SETTLED', settled_units = #{settledUnits},
                version = version + 1, last_updater_id = user_id
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND usage_key = #{usageKey}
              AND kind <> 'MODEL' AND status = 'RESERVED'
              AND reserved_units >= #{settledUnits}
            """)
    int settle(@Param("userId") String userId, @Param("scopeId") String scopeId,
               @Param("usageKey") String usageKey,
               @Param("settledUnits") long settledUnits);

    @Update("""
            UPDATE sq_budget_reservation
            SET status = 'SETTLED', settled_units = #{settledUnits},
                input_tokens = #{inputTokens}, output_tokens = #{outputTokens},
                version = version + 1, last_updater_id = user_id
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND usage_key = #{usageKey}
              AND kind = 'MODEL' AND status = 'RESERVED'
              AND reserved_units >= #{settledUnits}
            """)
    int settleModel(@Param("userId") String userId, @Param("scopeId") String scopeId,
                    @Param("usageKey") String usageKey,
                    @Param("settledUnits") long settledUnits,
                    @Param("inputTokens") long inputTokens,
                    @Param("outputTokens") long outputTokens);

    @Update("""
            UPDATE sq_budget_reservation
            SET status = 'UNKNOWN', version = version + 1, last_updater_id = user_id
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND usage_key = #{usageKey}
              AND kind = 'MODEL' AND status = 'RESERVED'
            """)
    int markModelUnknown(@Param("userId") String userId, @Param("scopeId") String scopeId,
                         @Param("usageKey") String usageKey);
    @Update("""
            UPDATE sq_budget_reservation
            SET status = 'UNKNOWN', version = version + 1, last_updater_id = user_id
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND usage_key = #{usageKey}
              AND status = 'RESERVED'
            """)
    int markUnknown(@Param("userId") String userId, @Param("scopeId") String scopeId,
                    @Param("usageKey") String usageKey);
}
