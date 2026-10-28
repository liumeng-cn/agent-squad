package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.CommandRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 持久命令和幂等收据 SQL 边界；按归属、作用域与调用键定位原命令。
 */
@Mapper
public interface CommandMapper {

    @Insert("""
            INSERT INTO sq_command
                (command_id, user_id, scope_key, idempotency_key, payload_hash,
                 task_id, binding_id, action, payload_json, status, due_at)
            VALUES
                (#{commandId}, #{userId}, #{scopeKey}, #{idempotencyKey}, #{payloadHash},
                 #{taskId}, #{bindingId}, 'SUBMIT', #{payloadJson}, 'PENDING', #{dueAt})
            """)
    int insertSubmit(@Param("commandId") String commandId, @Param("userId") String userId,
                     @Param("scopeKey") String scopeKey, @Param("idempotencyKey") String idempotencyKey,
                     @Param("payloadHash") String payloadHash, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("payloadJson") String payloadJson,
                     @Param("dueAt") LocalDateTime dueAt);

    @Insert("""
            INSERT INTO sq_command
                (command_id, user_id, scope_key, idempotency_key, payload_hash,
                 task_id, binding_id, action, payload_json, status, due_at)
            VALUES
                (#{commandId}, #{userId}, #{scopeKey}, #{idempotencyKey}, #{payloadHash},
                 #{taskId}, #{bindingId}, #{action}, #{payloadJson}, 'PENDING', #{dueAt})
            """)
    int insertControl(@Param("commandId") String commandId, @Param("userId") String userId,
                      @Param("scopeKey") String scopeKey, @Param("idempotencyKey") String idempotencyKey,
                      @Param("payloadHash") String payloadHash, @Param("taskId") String taskId,
                      @Param("bindingId") String bindingId, @Param("action") String action,
                      @Param("payloadJson") String payloadJson, @Param("dueAt") LocalDateTime dueAt);

    @Update("""
            UPDATE sq_command SET status = 'CANCELLED'
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND status IN ('PENDING', 'DELIVERING')
            """)
    int cancelPendingByTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @ConstructorArgs({
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "native_turn_id", javaType = String.class),
            @Arg(column = "native_run_id", javaType = String.class),
            @Arg(column = "native_status", javaType = String.class)
    })
    @Select("""
            SELECT command_id, user_id, task_id, binding_id, payload_hash, status,
                   native_turn_id, native_run_id, native_status
            FROM sq_command
            WHERE user_id = #{userId} AND scope_key = #{scopeKey}
              AND idempotency_key = #{idempotencyKey}
            """)
    CommandRow findByKey(@Param("userId") String userId, @Param("scopeKey") String scopeKey,
                         @Param("idempotencyKey") String idempotencyKey);

    @ConstructorArgs({
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "native_turn_id", javaType = String.class),
            @Arg(column = "native_run_id", javaType = String.class),
            @Arg(column = "native_status", javaType = String.class)
    })
    @Select("""
            SELECT command_id, user_id, task_id, binding_id, payload_hash, status,
                   native_turn_id, native_run_id, native_status
            FROM sq_command WHERE user_id = #{userId} AND command_id = #{commandId}
            """)
    CommandRow findOwned(@Param("userId") String userId, @Param("commandId") String commandId);

    @Select("""
            SELECT COUNT(*) FROM sq_command
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND action = 'MASTER_SIGNAL' AND native_turn_id = #{turnId}
            """)
    int countSignalTurn(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("turnId") String turnId);

    @Update("""
            UPDATE sq_command SET status = 'APPLIED'
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND native_turn_id = #{turnId} AND status = 'DELIVERED'
              AND action IN ('SUBMIT', 'MASTER_SIGNAL')
            """)
    int markMasterTurnApplied(@Param("userId") String userId, @Param("taskId") String taskId,
                              @Param("turnId") String turnId);

    @Select("""
            SELECT COUNT(*) FROM sq_command c
            JOIN sq_runtime_binding b ON b.user_id = c.user_id
                AND b.binding_id = c.binding_id AND b.role = 'MASTER'
            WHERE c.user_id = #{userId} AND c.task_id = #{taskId}
              AND c.action IN ('SUBMIT', 'MASTER_SIGNAL')
              AND c.status IN ('PENDING', 'DELIVERING', 'DELIVERED')
            """)
    int countUnprocessedMasterCommands(@Param("userId") String userId,
                                        @Param("taskId") String taskId);

    @Select("""
            SELECT command_id FROM sq_command
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND action = 'SUBMIT' AND status IN ('DELIVERED', 'APPLIED')
              AND native_turn_id IS NOT NULL
            ORDER BY id DESC LIMIT 1
            """)
    String findWorkerSubmitCommandId(@Param("userId") String userId,
                                      @Param("bindingId") String bindingId);

    @Select("""
            SELECT COUNT(*) FROM sq_command
            WHERE user_id = #{userId} AND binding_id = #{bindingId} AND action = 'RESUME'
            """)
    int countWorkerResumes(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("""
            SELECT COUNT(*) FROM sq_command
            WHERE user_id = #{userId} AND binding_id = #{bindingId} AND action = 'RESUME'
              AND status IN ('PENDING', 'DELIVERING')
            """)
    int countPendingWorkerResumes(@Param("userId") String userId, @Param("bindingId") String bindingId);
}
