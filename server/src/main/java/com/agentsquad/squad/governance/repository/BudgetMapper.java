package com.agentsquad.squad.governance.repository;

import com.agentsquad.squad.governance.domain.BudgetReservationRow;
import com.agentsquad.squad.governance.domain.TaskBudgetRow;
import org.apache.ibatis.annotations.*;

/**
 * 预算预留 SQL 边界；usage_key 同键幂等，UNKNOWN 只能由核对流程出账。
 */
@Mapper
public interface BudgetMapper {

    @Select("""
            SELECT task_id FROM sq_runtime_binding
            WHERE user_id = #{userId} AND framework_session_id = #{sessionId}
            """)
    String findTaskBySession(@Param("userId") String userId,
                             @Param("sessionId") String sessionId);

    @ConstructorArgs({
            @Arg(column = "budget_profile_key", javaType = String.class),
            @Arg(column = "budget_limit_microunits", javaType = long.class)
    })
    @Select("""
            SELECT budget_profile_key, budget_limit_microunits FROM sq_goal_task
            WHERE user_id = #{userId} AND task_id = #{taskId}
            FOR UPDATE
            """)
    TaskBudgetRow lockTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @Insert("""
            INSERT INTO sq_budget_reservation
                (reservation_id, user_id, task_id, usage_key, kind, reserved_units,
                 status, creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{reservationId}, #{userId}, #{taskId}, #{usageKey}, #{kind}, #{reservedUnits},
                 'RESERVED', #{userId}, #{creatorName}, #{userId}, #{creatorName})
            """)
    int insert(@Param("reservationId") String reservationId, @Param("userId") String userId,
               @Param("taskId") String taskId, @Param("usageKey") String usageKey,
               @Param("kind") String kind, @Param("reservedUnits") long reservedUnits,
               @Param("creatorName") String creatorName);

    @Insert("""
            INSERT INTO sq_budget_reservation
                (reservation_id, user_id, task_id, usage_key, kind, reserved_units,
                 model_profile_key, pricing_version, request_hash, status,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{reservationId}, #{userId}, #{taskId}, #{usageKey}, 'MODEL', #{reservedUnits},
                 #{modelProfileKey}, #{pricingVersion}, #{requestHash}, 'RESERVED',
                 #{userId}, '', #{userId}, '')
            """)
    int insertModel(@Param("reservationId") String reservationId, @Param("userId") String userId,
                    @Param("taskId") String taskId, @Param("usageKey") String usageKey,
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
            WHERE user_id = #{userId} AND task_id = #{taskId} AND usage_key = #{usageKey}
            """)
    BudgetReservationRow findReservation(@Param("userId") String userId,
                                         @Param("taskId") String taskId,
                                         @Param("usageKey") String usageKey);

    @Select("""
            SELECT status FROM sq_budget_reservation
            WHERE user_id = #{userId} AND task_id = #{taskId} AND usage_key = #{usageKey}
            """)
    String findStatus(@Param("userId") String userId, @Param("taskId") String taskId,
                      @Param("usageKey") String usageKey);

    @Select("""
            SELECT reserved_units FROM sq_budget_reservation
            WHERE user_id = #{userId} AND task_id = #{taskId} AND usage_key = #{usageKey}
            """)
    Long findReservedUnits(@Param("userId") String userId, @Param("taskId") String taskId,
                           @Param("usageKey") String usageKey);

    @Select("""
            SELECT COALESCE(SUM(CASE WHEN status = 'SETTLED' THEN settled_units
                                     ELSE reserved_units END), 0)
            FROM sq_budget_reservation
            WHERE user_id = #{userId} AND task_id = #{taskId} AND del_flag = '0'
            """)
    long sumConsumedUnits(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_budget_reservation
            SET status = 'SETTLED', settled_units = #{settledUnits},
                version = version + 1, last_updater_id = user_id
            WHERE user_id = #{userId} AND task_id = #{taskId} AND usage_key = #{usageKey}
              AND kind <> 'MODEL' AND status = 'RESERVED'
              AND reserved_units >= #{settledUnits}
            """)
    int settle(@Param("userId") String userId, @Param("taskId") String taskId,
               @Param("usageKey") String usageKey,
               @Param("settledUnits") long settledUnits);

    @Update("""
            UPDATE sq_budget_reservation
            SET status = 'SETTLED', settled_units = #{settledUnits},
                input_tokens = #{inputTokens}, output_tokens = #{outputTokens},
                version = version + 1, last_updater_id = user_id
            WHERE user_id = #{userId} AND task_id = #{taskId} AND usage_key = #{usageKey}
              AND kind = 'MODEL' AND status = 'RESERVED'
              AND reserved_units >= #{settledUnits}
            """)
    int settleModel(@Param("userId") String userId, @Param("taskId") String taskId,
                    @Param("usageKey") String usageKey,
                    @Param("settledUnits") long settledUnits,
                    @Param("inputTokens") long inputTokens,
                    @Param("outputTokens") long outputTokens);

    @Update("""
            UPDATE sq_budget_reservation
            SET status = 'UNKNOWN', version = version + 1, last_updater_id = user_id
            WHERE user_id = #{userId} AND task_id = #{taskId} AND usage_key = #{usageKey}
              AND kind = 'MODEL' AND status = 'RESERVED'
            """)
    int markModelUnknown(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("usageKey") String usageKey);
}
