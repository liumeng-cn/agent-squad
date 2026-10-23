package com.agentsquad.squad.governance.repository;

import org.apache.ibatis.annotations.*;

/**
 * 预算预留 SQL 边界；usage_key 同键幂等，UNKNOWN 只能由核对流程出账。
 */
@Mapper
public interface BudgetMapper {

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
              AND status = 'RESERVED'
            """)
    int settle(@Param("userId") String userId, @Param("taskId") String taskId,
               @Param("usageKey") String usageKey,
               @Param("settledUnits") long settledUnits);
}
