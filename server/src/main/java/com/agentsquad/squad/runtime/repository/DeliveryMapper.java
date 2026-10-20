package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.DeliveryRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 持久命令的领取、固定 Binding 读取与原生提交收据写入。 */
@Mapper
public interface DeliveryMapper {

    @Select("""
            SELECT command_id FROM sq_command
            WHERE action = 'SUBMIT'
              AND ((status = 'PENDING' AND due_at <= #{now})
                   OR (status = 'DELIVERING' AND delivery_lease_until < #{now}))
            ORDER BY due_at, id LIMIT 20
            """)
    List<String> findDueIds(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_command
            SET status = 'DELIVERING', delivery_token = #{token},
                delivery_lease_until = #{leaseUntil}, delivery_attempts = delivery_attempts + 1,
                last_error_code = NULL
            WHERE command_id = #{commandId} AND action = 'SUBMIT'
              AND ((status = 'PENDING' AND due_at <= #{now})
                   OR (status = 'DELIVERING' AND delivery_lease_until < #{now}))
            """)
    int claim(@Param("commandId") String commandId, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @ConstructorArgs({
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "delivery_attempts", javaType = int.class),
            @Arg(column = "agent_id", javaType = String.class),
            @Arg(column = "framework_session_id", javaType = String.class)
    })
    @Select("""
            SELECT c.command_id, c.user_id, c.task_id, c.binding_id, c.payload_json,
                   c.delivery_attempts, b.agent_id, b.framework_session_id
            FROM sq_command c
            JOIN sq_runtime_binding b ON b.user_id = c.user_id
                AND b.binding_id = c.binding_id AND b.task_id = c.task_id
            WHERE c.command_id = #{commandId} AND c.delivery_token = #{token}
              AND c.status = 'DELIVERING' AND c.action = 'SUBMIT'
            """)
    DeliveryRow findClaimed(@Param("commandId") String commandId, @Param("token") String token);

    @Update("""
            UPDATE sq_command SET status = CASE WHEN #{nativeStatus} = 'rejected' THEN 'BLOCKED'
                                                 ELSE 'DELIVERED' END,
                delivery_token = NULL,
                delivery_lease_until = NULL, native_turn_id = #{turnId},
                native_run_id = #{runId}, native_status = #{nativeStatus}, last_error_code = NULL
            WHERE command_id = #{commandId} AND delivery_token = #{token}
              AND status = 'DELIVERING' AND delivery_lease_until > #{now}
            """)
    int saveReceipt(@Param("commandId") String commandId, @Param("token") String token,
                    @Param("turnId") String turnId, @Param("runId") String runId,
                    @Param("nativeStatus") String nativeStatus, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_command SET status = 'PENDING', delivery_token = NULL,
                delivery_lease_until = NULL, due_at = #{dueAt}, last_error_code = #{errorCode}
            WHERE command_id = #{commandId} AND delivery_token = #{token}
              AND status = 'DELIVERING'
            """)
    int retry(@Param("commandId") String commandId, @Param("token") String token,
              @Param("dueAt") LocalDateTime dueAt, @Param("errorCode") String errorCode);
}
