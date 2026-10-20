package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.CommandRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 持久命令和幂等收据 SQL 边界；按归属、作用域与调用键定位原命令。 */
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

    @ConstructorArgs({
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "status", javaType = String.class)
    })
    @Select("""
            SELECT command_id, user_id, task_id, binding_id, payload_hash, status
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
            @Arg(column = "status", javaType = String.class)
    })
    @Select("""
            SELECT command_id, user_id, task_id, binding_id, payload_hash, status
            FROM sq_command WHERE user_id = #{userId} AND command_id = #{commandId}
            """)
    CommandRow findOwned(@Param("userId") String userId, @Param("commandId") String commandId);
}
