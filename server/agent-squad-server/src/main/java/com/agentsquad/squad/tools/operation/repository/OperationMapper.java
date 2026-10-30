package com.agentsquad.squad.tools.operation.repository;

import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.domain.ToolExecutionContextRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部操作账本 SQL 边界；按原生调用键与业务意图键双路幂等，状态推进全部带版本 CAS。
 */
@Mapper
public interface OperationMapper {

    @ConstructorArgs({
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "task_status", javaType = String.class),
            @Arg(column = "cancellation_requested", javaType = boolean.class),
            @Arg(column = "scope_id", javaType = String.class)
    })
    @Select("""
            SELECT CASE WHEN s.kind = 'TASK' THEN s.owner_id ELSE NULL END AS task_id, b.binding_id, COALESCE(t.status, r.status) AS task_status,
                   (COALESCE(t.cancel_requested_at, r.cancel_requested_at) IS NOT NULL) AS cancellation_requested,
                   b.scope_id FROM sq_runtime_binding b
            JOIN sq_execution_scope s ON s.user_id = b.user_id AND s.scope_id = b.scope_id
            LEFT JOIN sq_goal_task t ON s.kind = 'TASK' AND t.user_id = s.user_id AND t.task_id = s.owner_id
            LEFT JOIN sq_response_execution r ON s.kind = 'RESPONSE' AND r.user_id = s.user_id AND r.execution_id = s.owner_id
            WHERE b.user_id = #{userId} AND b.framework_session_id = #{sessionId} AND b.role = 'MASTER'
              AND b.del_flag = '0'
            """)
    ToolExecutionContextRow lockToolContext(@Param("userId") String userId, @Param("sessionId") String sessionId);

    @Insert("""
            INSERT INTO sq_operation
                (operation_id, user_id, task_id, job_id, binding_id, framework_turn_id, tool_call_id,
                 tool_key, tool_version, business_intent_key, effect, connection_id, connection_revision,
                 auth_version, request_hash, arguments_json, status, version, scope_id, budget_usage_key,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{operationId}, #{userId}, #{taskId}, #{jobId}, #{bindingId}, #{frameworkTurnId},
                 #{toolCallId}, #{toolKey}, #{toolVersion}, #{businessIntentKey}, #{effect},
                 #{connectionId}, #{connectionRevision}, #{authVersion}, #{requestHash},
                 #{argumentsJson}, #{status}, 0,
                 (SELECT scope_id FROM sq_runtime_binding WHERE user_id = #{userId} AND binding_id = #{bindingId}), #{budgetUsageKey},
                 #{userId}, #{creatorName}, #{userId}, #{creatorName})
            """)
    int insert(@Param("operationId") String operationId, @Param("userId") String userId,
               @Param("taskId") String taskId, @Param("jobId") String jobId,
               @Param("bindingId") String bindingId, @Param("frameworkTurnId") String frameworkTurnId,
               @Param("toolCallId") String toolCallId, @Param("toolKey") String toolKey,
               @Param("toolVersion") String toolVersion,
               @Param("businessIntentKey") String businessIntentKey, @Param("effect") String effect,
               @Param("connectionId") String connectionId,
               @Param("connectionRevision") long connectionRevision,
               @Param("authVersion") long authVersion, @Param("requestHash") String requestHash,
               @Param("argumentsJson") String argumentsJson, @Param("status") String status,
               @Param("creatorName") String creatorName, @Param("budgetUsageKey") String budgetUsageKey);

    @ConstructorArgs({
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "framework_turn_id", javaType = String.class),
            @Arg(column = "tool_call_id", javaType = String.class),
            @Arg(column = "tool_key", javaType = String.class),
            @Arg(column = "tool_version", javaType = String.class),
            @Arg(column = "business_intent_key", javaType = String.class),
            @Arg(column = "effect", javaType = String.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "auth_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "arguments_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "executor_owner", javaType = String.class),
            @Arg(column = "executor_fence", javaType = long.class),
            @Arg(column = "external_key", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "budget_usage_key", javaType = String.class)
    })
    @Select("""
            SELECT operation_id, user_id, task_id, job_id, binding_id, framework_turn_id,
                   tool_call_id, tool_key, tool_version, business_intent_key, effect,
                   connection_id, connection_revision, auth_version, request_hash,
                   arguments_json, status, version, executor_owner, executor_fence,
                   external_key, result_json, scope_id, budget_usage_key
            FROM sq_operation
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND framework_turn_id = #{frameworkTurnId} AND tool_call_id = #{toolCallId} FOR UPDATE
            """)
    OperationRow findByNativeCall(@Param("userId") String userId,
                                  @Param("bindingId") String bindingId,
                                  @Param("frameworkTurnId") String frameworkTurnId,
                                  @Param("toolCallId") String toolCallId);

    @ConstructorArgs({
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "framework_turn_id", javaType = String.class),
            @Arg(column = "tool_call_id", javaType = String.class),
            @Arg(column = "tool_key", javaType = String.class),
            @Arg(column = "tool_version", javaType = String.class),
            @Arg(column = "business_intent_key", javaType = String.class),
            @Arg(column = "effect", javaType = String.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "auth_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "arguments_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "executor_owner", javaType = String.class),
            @Arg(column = "executor_fence", javaType = long.class),
            @Arg(column = "external_key", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "budget_usage_key", javaType = String.class)
    })
    @Select("""
            SELECT operation_id, user_id, task_id, job_id, binding_id, framework_turn_id,
                   tool_call_id, tool_key, tool_version, business_intent_key, effect,
                   connection_id, connection_revision, auth_version, request_hash,
                   arguments_json, status, version, executor_owner, executor_fence,
                   external_key, result_json, scope_id, budget_usage_key
            FROM sq_operation
            WHERE user_id = #{userId} AND scope_id = #{scopeId} AND tool_key = #{toolKey}
              AND business_intent_key = #{businessIntentKey} FOR UPDATE
            """)
    OperationRow findByBusinessIntent(@Param("userId") String userId,
                                      @Param("scopeId") String scopeId,
                                      @Param("toolKey") String toolKey,
                                      @Param("businessIntentKey") String businessIntentKey);

    @ConstructorArgs({
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "framework_turn_id", javaType = String.class),
            @Arg(column = "tool_call_id", javaType = String.class),
            @Arg(column = "tool_key", javaType = String.class),
            @Arg(column = "tool_version", javaType = String.class),
            @Arg(column = "business_intent_key", javaType = String.class),
            @Arg(column = "effect", javaType = String.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "auth_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "arguments_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "executor_owner", javaType = String.class),
            @Arg(column = "executor_fence", javaType = long.class),
            @Arg(column = "external_key", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "budget_usage_key", javaType = String.class)
    })
    @Select("""
            SELECT operation_id, user_id, task_id, job_id, binding_id, framework_turn_id,
                   tool_call_id, tool_key, tool_version, business_intent_key, effect,
                   connection_id, connection_revision, auth_version, request_hash,
                   arguments_json, status, version, executor_owner, executor_fence,
                   external_key, result_json, scope_id, budget_usage_key
            FROM sq_operation
            WHERE user_id = #{userId} AND operation_id = #{operationId}
            """)
    OperationRow findOwned(@Param("userId") String userId,
                           @Param("operationId") String operationId);

    @ConstructorArgs({
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "framework_turn_id", javaType = String.class),
            @Arg(column = "tool_call_id", javaType = String.class),
            @Arg(column = "tool_key", javaType = String.class),
            @Arg(column = "tool_version", javaType = String.class),
            @Arg(column = "business_intent_key", javaType = String.class),
            @Arg(column = "effect", javaType = String.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "auth_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "arguments_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "executor_owner", javaType = String.class),
            @Arg(column = "executor_fence", javaType = long.class),
            @Arg(column = "external_key", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "budget_usage_key", javaType = String.class)
    })
    @Select("""
            <script>SELECT operation_id, user_id, task_id, job_id, binding_id, framework_turn_id,
                   tool_call_id, tool_key, tool_version, business_intent_key, effect,
                   connection_id, connection_revision, auth_version, request_hash,
                   arguments_json, status, version, executor_owner, executor_fence,
                   external_key, result_json, scope_id, budget_usage_key
            FROM sq_operation
            WHERE user_id = #{userId} AND task_id = #{taskId} AND del_flag = '0'
              <if test="cursorId != null">AND id &lt; #{cursorId}</if>
            ORDER BY id DESC
            LIMIT #{limit}</script>
            """)
    List<OperationRow> listByTask(@Param("userId") String userId, @Param("taskId") String taskId,
                                  @Param("cursorId") Long cursorId, @Param("limit") int limit);

    @Select("""
            SELECT id FROM sq_operation
            WHERE user_id = #{userId} AND operation_id = #{operationId} AND del_flag = '0'
            """)
    Long cursorIdOf(@Param("userId") String userId, @Param("operationId") String operationId);

    @Update("""
            UPDATE sq_operation
            SET status = 'WAITING_APPROVAL', version = version + 1,
                last_updater_id = user_id
            WHERE user_id = #{userId} AND operation_id = #{operationId}
              AND status = 'PREPARED' AND version = #{expectedVersion}
            """)
    int markWaitingApproval(@Param("userId") String userId,
                            @Param("operationId") String operationId,
                            @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE sq_operation
            SET status = 'IN_FLIGHT', executor_owner = #{owner}, executor_fence = #{fence},
                executor_until = #{leaseUntil}, external_key = #{externalKey},
                version = version + 1, last_updater_id = #{owner}
            WHERE user_id = #{userId} AND operation_id = #{operationId}
              AND status IN ('PREPARED', 'WAITING_APPROVAL') AND version = #{expectedVersion}
              AND EXISTS (SELECT 1 FROM sq_execution_scope s JOIN sq_conversation c
                  ON c.user_id = s.user_id AND c.conversation_id = s.conversation_id
                  WHERE s.user_id = sq_operation.user_id AND s.scope_id = sq_operation.scope_id
                    AND s.del_flag = '0' AND c.del_flag = '0' AND c.status = 'ACTIVE'
                    AND c.generation = s.conversation_generation
                    AND ((s.kind = 'TASK' AND EXISTS (SELECT 1 FROM sq_goal_task t
                        WHERE t.user_id = s.user_id AND t.task_id = s.owner_id AND t.scope_id = s.scope_id
                          AND t.cancel_requested_at IS NULL AND t.del_flag = '0'
                          AND t.status IN ('QUEUED', 'RUNNING', 'WAITING_APPROVAL', 'WAITING_INPUT', 'WAITING_WORKERS')))
                      OR (s.kind = 'RESPONSE' AND EXISTS (SELECT 1 FROM sq_response_execution r
                        WHERE r.user_id = s.user_id AND r.execution_id = s.owner_id AND r.scope_id = s.scope_id
                          AND r.cancel_requested_at IS NULL AND r.del_flag = '0'
                          AND r.status IN ('QUEUED', 'RUNNING', 'WAITING_APPROVAL', 'WAITING_INPUT')))))
            """)
    int markInFlight(@Param("userId") String userId, @Param("operationId") String operationId,
                     @Param("expectedVersion") long expectedVersion, @Param("owner") String owner,
                     @Param("fence") long fence,
                     @Param("leaseUntil") LocalDateTime leaseUntil,
                     @Param("externalKey") String externalKey);

    @Update("""
            UPDATE sq_operation
            SET status = #{status}, result_json = #{resultJson},
                version = version + 1, last_updater_id = #{updaterId}
            WHERE user_id = #{userId} AND operation_id = #{operationId}
              AND status = 'IN_FLIGHT' AND version = #{expectedVersion}
              AND executor_owner = #{owner} AND executor_fence = #{fence}
            """)
    int completeInFlight(@Param("userId") String userId,
                         @Param("operationId") String operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("owner") String owner, @Param("fence") long fence,
                         @Param("status") String status,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") String updaterId);

    @Update("""
            UPDATE sq_operation
            SET status = 'UNKNOWN', version = version + 1, last_updater_id = #{updaterId}
            WHERE user_id = #{userId} AND operation_id = #{operationId}
              AND status = 'IN_FLIGHT' AND version = #{expectedVersion}
            """)
    int markUnknown(@Param("userId") String userId, @Param("operationId") String operationId,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("updaterId") String updaterId);

    @ConstructorArgs({
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "framework_turn_id", javaType = String.class),
            @Arg(column = "tool_call_id", javaType = String.class),
            @Arg(column = "tool_key", javaType = String.class),
            @Arg(column = "tool_version", javaType = String.class),
            @Arg(column = "business_intent_key", javaType = String.class),
            @Arg(column = "effect", javaType = String.class),
            @Arg(column = "connection_id", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "auth_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "arguments_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "executor_owner", javaType = String.class),
            @Arg(column = "executor_fence", javaType = long.class),
            @Arg(column = "external_key", javaType = String.class),
            @Arg(column = "result_json", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class),
            @Arg(column = "budget_usage_key", javaType = String.class)
    })
    @Select("""
            SELECT operation_id, user_id, task_id, job_id, binding_id, framework_turn_id,
                   tool_call_id, tool_key, tool_version, business_intent_key, effect,
                   connection_id, connection_revision, auth_version, request_hash,
                   arguments_json, status, version, executor_owner, executor_fence,
                   external_key, result_json, scope_id, budget_usage_key
            FROM sq_operation
            WHERE status = 'IN_FLIGHT' AND executor_until < #{now} AND del_flag = '0'
            ORDER BY id
            LIMIT #{limit}
            """)
    List<OperationRow> findExpiredInFlight(@Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    @Update("""
            UPDATE sq_operation
            SET status = #{status}, result_json = #{resultJson},
                version = version + 1, last_updater_id = #{updaterId}
            WHERE user_id = #{userId} AND operation_id = #{operationId}
              AND status = 'UNKNOWN' AND version = #{expectedVersion}
            """)
    int reconcileOutcome(@Param("userId") String userId,
                         @Param("operationId") String operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("status") String status,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") String updaterId);

    @Update("""
            UPDATE sq_operation
            SET result_json = #{resultJson}, version = version + 1, last_updater_id = #{updaterId}
            WHERE user_id = #{userId} AND operation_id = #{operationId}
              AND status = 'UNKNOWN' AND version = #{expectedVersion}
            """)
    int recordUnresolved(@Param("userId") String userId,
                         @Param("operationId") String operationId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("resultJson") String resultJson,
                         @Param("updaterId") String updaterId);

    @Update("""
            UPDATE sq_operation
            SET status = 'CANCELLED', result_json = #{resultJson},
                version = version + 1, last_updater_id = #{updaterId}
            WHERE user_id = #{userId} AND operation_id = #{operationId}
              AND status = 'WAITING_APPROVAL' AND version = #{expectedVersion}
            """)
    int cancelWaitingApproval(@Param("userId") String userId,
                              @Param("operationId") String operationId,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("resultJson") String resultJson,
                              @Param("updaterId") String updaterId);

    @Select("""
            SELECT o.operation_id FROM sq_operation o JOIN sq_execution_scope s
                ON s.user_id = o.user_id AND s.scope_id = o.scope_id
            WHERE o.user_id = #{userId} AND o.status IN ('UNKNOWN', 'RECONCILING') AND o.del_flag = '0'
              AND s.conversation_id = (SELECT conversation_id FROM sq_execution_scope
                  WHERE user_id = #{userId} AND scope_id = #{scopeId})
            FOR UPDATE
            """)
    List<String> findUncertainInConversation(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
