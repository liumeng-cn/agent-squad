package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.DeliveryRow;
import com.agentsquad.squad.conversation.domain.CommandClaimContext;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久命令的领取、固定 Binding 读取与原生提交收据写入。
 */
@Mapper
public interface DeliveryMapper {

    String MESSAGE_ORDER_GUARD = """
              AND (sq_command.action <> 'MESSAGE_SUBMIT' OR NOT EXISTS (
                  SELECT 1
                  FROM sq_response_execution current_reply
                  JOIN sq_execution_scope current_scope
                    ON current_scope.user_id = current_reply.user_id
                   AND current_scope.scope_id = current_reply.scope_id
                  JOIN sq_message current_message
                    ON current_message.user_id = current_reply.user_id
                   AND current_message.message_id = current_reply.source_message_id
                  JOIN sq_response_execution earlier_reply
                    ON earlier_reply.user_id = current_reply.user_id
                   AND earlier_reply.conversation_id = current_reply.conversation_id
                  JOIN sq_execution_scope earlier_scope
                    ON earlier_scope.user_id = earlier_reply.user_id
                   AND earlier_scope.scope_id = earlier_reply.scope_id
                   AND earlier_scope.conversation_generation = current_scope.conversation_generation
                  JOIN sq_message earlier_message
                    ON earlier_message.user_id = earlier_reply.user_id
                   AND earlier_message.message_id = earlier_reply.source_message_id
                  WHERE current_reply.user_id = sq_command.user_id
                    AND current_reply.binding_id = sq_command.binding_id
                    AND COALESCE(earlier_reply.dispatch_sequence, earlier_message.message_seq) < COALESCE(current_reply.dispatch_sequence, current_message.message_seq)
                    AND earlier_reply.del_flag = '0'
                    AND earlier_reply.status IN ('QUEUED', 'RUNNING', 'RECOVERING', 'RECONCILING', 'CANCELLING')
              ))
            """;

    @Select("""
            SELECT command_id FROM sq_command
            WHERE action IN ('SUBMIT', 'MESSAGE_SUBMIT', 'MASTER_SIGNAL', 'TASK_DIRECTIVE', 'STOP', 'RESUME', 'RESPOND')
              AND ((status = 'PENDING' AND due_at <= #{now})
                   OR (status = 'DELIVERING' AND delivery_lease_until < #{now}))
            """ + MESSAGE_ORDER_GUARD + " ORDER BY due_at, id LIMIT 20")
    List<String> findDueIds(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_command
            SET status = 'DELIVERING', delivery_token = #{token},
                delivery_lease_until = #{leaseUntil}, delivery_attempts = delivery_attempts + 1,
                last_error_code = NULL
            WHERE command_id = #{commandId}
              AND action IN ('SUBMIT', 'MESSAGE_SUBMIT', 'MASTER_SIGNAL', 'TASK_DIRECTIVE', 'STOP', 'RESUME', 'RESPOND')
              AND ((status = 'PENDING' AND due_at <= #{now})
                   OR (status = 'DELIVERING' AND delivery_lease_until < #{now}))
            """)
    int claim(@Param("commandId") String commandId, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "action", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class)
    })
    @Select("""
            SELECT user_id,
                   scope_id,
                   action,
                   binding_id
            FROM sq_command
            WHERE command_id = #{commandId}
            """)
    CommandClaimContext claimContext(@Param("commandId") String commandId);

    @Select("""
            SELECT earlier_reply.execution_id
            FROM sq_response_execution current_reply
            JOIN sq_execution_scope current_scope
              ON current_scope.user_id = current_reply.user_id
             AND current_scope.scope_id = current_reply.scope_id
            JOIN sq_message current_message
              ON current_message.user_id = current_reply.user_id
             AND current_message.message_id = current_reply.source_message_id
            JOIN sq_response_execution earlier_reply
              ON earlier_reply.user_id = current_reply.user_id
             AND earlier_reply.conversation_id = current_reply.conversation_id
            JOIN sq_execution_scope earlier_scope
              ON earlier_scope.user_id = earlier_reply.user_id
             AND earlier_scope.scope_id = earlier_reply.scope_id
             AND earlier_scope.conversation_generation = current_scope.conversation_generation
            JOIN sq_message earlier_message
              ON earlier_message.user_id = earlier_reply.user_id
             AND earlier_message.message_id = earlier_reply.source_message_id
            WHERE current_reply.user_id = #{userId}
              AND current_reply.binding_id = #{bindingId}
              AND COALESCE(earlier_reply.dispatch_sequence, earlier_message.message_seq) < COALESCE(current_reply.dispatch_sequence, current_message.message_seq)
              AND earlier_reply.del_flag = '0'
              AND earlier_reply.status IN ('QUEUED', 'RUNNING', 'RECOVERING', 'RECONCILING', 'CANCELLING')
            FOR UPDATE
            """)
    List<String> earlierBlockingReplies(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @ConstructorArgs({
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "action", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "delivery_attempts", javaType = int.class),
            @Arg(column = "agent_id", javaType = String.class),
            @Arg(column = "framework_session_id", javaType = String.class)
    })
    @Select("""
            SELECT c.command_id, c.user_id, c.task_id, c.binding_id, c.action, c.payload_json,
                   c.delivery_attempts, b.agent_id, b.framework_session_id
            FROM sq_command c
            JOIN sq_runtime_binding b ON b.user_id = c.user_id
                AND b.binding_id = c.binding_id AND b.scope_id = c.scope_id
            WHERE c.command_id = #{commandId} AND c.delivery_token = #{token}
              AND c.status = 'DELIVERING'
            """)
    DeliveryRow findClaimed(@Param("commandId") String commandId, @Param("token") String token);

    @Select("""
            SELECT native_turn_id FROM sq_command
            WHERE user_id = #{userId}
              AND binding_id = #{bindingId} AND action IN ('SUBMIT','MESSAGE_SUBMIT','MASTER_SIGNAL','TASK_DIRECTIVE')
              AND native_turn_id IS NOT NULL
            ORDER BY id DESC LIMIT 1
            """)
    String findSubmitTurnId(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_command SET status = CASE WHEN #{nativeStatus} = 'rejected' THEN 'BLOCKED'
                                                 WHEN action IN ('SUBMIT', 'MASTER_SIGNAL', 'TASK_DIRECTIVE') AND EXISTS (
                     SELECT 1 FROM sq_runtime_binding b
                     WHERE b.binding_id = sq_command.binding_id
                       AND b.user_id = sq_command.user_id
                       AND b.role = 'MASTER'
                       AND b.task_id IS NOT NULL
                       AND b.last_ended_turn_id = #{turnId}
                 ) THEN 'APPLIED' ELSE 'DELIVERED' END,
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

    @Update("""
            UPDATE sq_command SET status = 'BLOCKED', delivery_token = NULL,
                delivery_lease_until = NULL, last_error_code = #{errorCode}
            WHERE command_id = #{commandId} AND delivery_token = #{token}
              AND status = 'DELIVERING' AND action = 'RESUME'
            """)
    int blockRejectedResume(@Param("commandId") String commandId, @Param("token") String token,
                            @Param("errorCode") String errorCode);
}
