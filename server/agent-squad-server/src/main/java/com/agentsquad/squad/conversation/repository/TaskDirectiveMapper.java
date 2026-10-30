package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.TaskDirectiveRow;
import com.agentsquad.squad.conversation.domain.TaskDirectiveTarget;
import com.agentsquad.squad.conversation.domain.TaskDirectiveFact;
import com.agentsquad.squad.conversation.domain.TaskGoalState;
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
 * 用户目标修改的持久事实边界。
 * <p>
 * 调用方先锁会话和实际所有者；状态、版本和安全点检查均使用锁后当前读，不用旧快照决定是否应用。
 */
@Mapper
public interface TaskDirectiveMapper {
    @ConstructorArgs({
            @Arg(column = "directive_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "source_scope_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "logical_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "expected_version", javaType = long.class),
            @Arg(column = "expected_goal_revision", javaType = long.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "source_text", javaType = String.class),
            @Arg(column = "artifact_ids_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "applied_version", javaType = Long.class),
            @Arg(column = "applied_goal_revision", javaType = Long.class),
            @Arg(column = "reason_code", javaType = String.class),
            @Arg(column = "reason_message", javaType = String.class)
    })
    @Select("""
            SELECT directive_id,
                   user_id,
                   conversation_id,
                   conversation_generation,
                   source_message_id,
                   source_scope_id,
                   task_id,
                   logical_key,
                   payload_hash,
                   expected_version,
                   expected_goal_revision,
                   kind,
                   content,
                   source_text,
                   artifact_ids_json,
                   status,
                   command_id,
                   applied_version,
                   applied_goal_revision,
                   reason_code,
                   reason_message
            FROM sq_task_directive
            WHERE user_id = #{userId}
              AND directive_id = #{directiveId}
              AND del_flag = '0' FOR UPDATE
            """)
    TaskDirectiveRow lock(@Param("userId") String userId, @Param("directiveId") String directiveId);

    @ConstructorArgs({
            @Arg(column = "directive_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "source_scope_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "logical_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "expected_version", javaType = long.class),
            @Arg(column = "expected_goal_revision", javaType = long.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "source_text", javaType = String.class),
            @Arg(column = "artifact_ids_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "applied_version", javaType = Long.class),
            @Arg(column = "applied_goal_revision", javaType = Long.class),
            @Arg(column = "reason_code", javaType = String.class),
            @Arg(column = "reason_message", javaType = String.class)
    })
    @Select("""
            SELECT directive_id,
                   user_id,
                   conversation_id,
                   conversation_generation,
                   source_message_id,
                   source_scope_id,
                   task_id,
                   logical_key,
                   payload_hash,
                   expected_version,
                   expected_goal_revision,
                   kind,
                   content,
                   source_text,
                   artifact_ids_json,
                   status,
                   command_id,
                   applied_version,
                   applied_goal_revision,
                   reason_code,
                   reason_message
            FROM sq_task_directive
            WHERE user_id = #{userId}
              AND source_scope_id = #{scopeId}
              AND logical_key = #{key} FOR UPDATE
            """)
    TaskDirectiveRow findByKey(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("key") String key);

    @ConstructorArgs({
            @Arg(column = "directive_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "source_scope_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "logical_key", javaType = String.class),
            @Arg(column = "payload_hash", javaType = String.class),
            @Arg(column = "expected_version", javaType = long.class),
            @Arg(column = "expected_goal_revision", javaType = long.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "source_text", javaType = String.class),
            @Arg(column = "artifact_ids_json", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "command_id", javaType = String.class),
            @Arg(column = "applied_version", javaType = Long.class),
            @Arg(column = "applied_goal_revision", javaType = Long.class),
            @Arg(column = "reason_code", javaType = String.class),
            @Arg(column = "reason_message", javaType = String.class)
    })
    @Select("""
            SELECT directive_id,
                   user_id,
                   conversation_id,
                   conversation_generation,
                   source_message_id,
                   source_scope_id,
                   task_id,
                   logical_key,
                   payload_hash,
                   expected_version,
                   expected_goal_revision,
                   kind,
                   content,
                   source_text,
                   artifact_ids_json,
                   status,
                   command_id,
                   applied_version,
                   applied_goal_revision,
                   reason_code,
                   reason_message
            FROM sq_task_directive
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND status = 'APPLIED'
              AND applied_goal_revision >= #{revision}
              AND del_flag = '0'
            ORDER BY applied_goal_revision FOR UPDATE
            """)
    List<TaskDirectiveRow> applied(@Param("userId") String userId, @Param("taskId") String taskId, @Param("revision") long revision);

    @Insert("""
            INSERT INTO sq_task_directive (
                directive_id,
                user_id,
                conversation_id,
                conversation_generation,
                source_message_id,
                source_scope_id,
                task_id,
                logical_key,
                payload_hash,
                expected_version,
                expected_goal_revision,
                kind,
                content,
                source_text,
                artifact_ids_json,
                status,
                next_attempt_at,
                creator_id,
                last_updater_id
            ) VALUES (
                #{row.directiveId},
                #{row.userId},
                #{row.conversationId},
                #{row.generation},
                #{row.sourceMessageId},
                #{row.sourceScopeId},
                #{row.taskId},
                #{row.logicalKey},
                #{row.payloadHash},
                #{row.expectedVersion},
                #{row.expectedGoalRevision},
                #{row.kind},
                #{row.content},
                #{row.sourceText},
                #{row.artifactIdsJson},
                'ACCEPTED',
                #{now},
                #{row.userId},
                #{row.userId}
            )
            """)
    int insert(@Param("row") TaskDirectiveRow row, @Param("now") LocalDateTime now);

    @ConstructorArgs({
            @Arg(column = "goal_revision", javaType = long.class),
            @Arg(column = "input_json", javaType = String.class)
    })
    @Select("""
            SELECT goal_revision,
                   input_json
            FROM sq_goal_task
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
            FOR UPDATE
            """)
    TaskGoalState goalState(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT directive_id
            FROM sq_task_directive
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND status = 'ACCEPTED'
              AND del_flag = '0'
            ORDER BY id
            FOR UPDATE
            """)
    List<String> pending(@Param("userId") String userId, @Param("taskId") String taskId);

    @ConstructorArgs({
            @Arg(column = "directive_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class)
    })
    @Select("""
            SELECT directive_id,
                   user_id,
                   task_id
            FROM sq_task_directive
            WHERE status = 'ACCEPTED'
              AND next_attempt_at <= #{now}
              AND del_flag = '0'
            ORDER BY next_attempt_at, id
            LIMIT 20
            """)
    List<TaskDirectiveTarget> due(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_task_directive
            SET next_attempt_at = #{next}
            WHERE user_id = #{userId}
              AND directive_id = #{directiveId}
              AND status = 'ACCEPTED'
            """)
    int defer(@Param("userId") String userId, @Param("directiveId") String directiveId, @Param("next") LocalDateTime next);

    @Update("""
            UPDATE sq_task_directive
            SET command_id = #{commandId}
            WHERE user_id = #{userId}
              AND directive_id = #{directiveId}
              AND status = 'ACCEPTED'
              AND command_id IS NULL
            """)
    int bindCommand(@Param("userId") String userId, @Param("directiveId") String directiveId, @Param("commandId") String commandId);

    @Update("""
            UPDATE sq_task_directive
            SET status                = #{status},
                applied_version       = #{version},
                applied_goal_revision = #{revision},
                reason_code           = #{code},
                reason_message        = #{message}
            WHERE user_id = #{userId}
              AND directive_id = #{directiveId}
              AND status = 'ACCEPTED'
            """)
    int finish(@Param("userId") String userId, @Param("directiveId") String directiveId,
               @Param("status") String status, @Param("version") Long version, @Param("revision") Long revision,
               @Param("code") String code, @Param("message") String message);

    @Update("""
            UPDATE sq_goal_task
            SET goal_revision               = goal_revision + 1,
                version                     = version + 1,
                answer_revision             = answer_revision + 1,
                input_json                  = #{input},
                completion_proposal_json    = NULL,
                proposal_binding_id         = NULL,
                proposal_turn_id            = NULL,
                proposal_tool_call_id       = NULL,
                proposal_hash               = NULL
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND version = #{version}
              AND goal_revision = #{revision}
              AND cancel_requested_at IS NULL
              AND status IN ('QUEUED', 'RUNNING')
            """)
    int applyGoal(@Param("userId") String userId, @Param("taskId") String taskId,
                  @Param("version") long version, @Param("revision") long revision, @Param("input") String input);

    @Update("""
            UPDATE sq_goal_task
            SET completion_proposal_json = NULL,
                proposal_binding_id      = NULL,
                proposal_turn_id         = NULL,
                proposal_tool_call_id    = NULL,
                proposal_hash            = NULL
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
            """)
    int clearCandidate(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT operation_id
            FROM sq_operation
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
              AND status IN ('PREPARED', 'WAITING_APPROVAL', 'IN_FLIGHT', 'UNKNOWN', 'RECONCILING')
              AND del_flag = '0'
            FOR UPDATE
            """)
    List<String> openOperations(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Select("""
            SELECT interaction_id
            FROM sq_interaction
            WHERE user_id = #{userId}
              AND scope_id = #{scopeId}
              AND status = 'OPEN'
              AND del_flag = '0'
            FOR UPDATE
            """)
    List<String> openInteractions(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @ConstructorArgs({
            @Arg(column = "directive_id", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "applied_version", javaType = Long.class),
            @Arg(column = "applied_goal_revision", javaType = Long.class),
            @Arg(column = "reason_code", javaType = String.class),
            @Arg(column = "reason_message", javaType = String.class)
    })
    @Select("""
            SELECT directive_id,
                   source_message_id,
                   kind,
                   status,
                   applied_version,
                   applied_goal_revision,
                   reason_code,
                   reason_message
            FROM sq_task_directive
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND del_flag = '0'
            ORDER BY id DESC
            LIMIT 20
            FOR UPDATE
            """)
    List<TaskDirectiveFact> latestFacts(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT applied_goal_revision
            FROM sq_task_directive
            WHERE user_id = #{userId}
              AND task_id = #{taskId}
              AND kind = 'REPLACE'
              AND status = 'APPLIED'
              AND del_flag = '0'
            ORDER BY applied_goal_revision DESC
            LIMIT 1 FOR UPDATE
            """)
    Long lastReplacement(@Param("userId") String userId, @Param("taskId") String taskId);

    default List<TaskDirectiveRow> effectiveApplied(String userId, String taskId) {
        Long revision = lastReplacement(userId, taskId);
        return applied(userId, taskId, revision == null ? 0 : revision);
    }

    @Select("""
            SELECT c.command_id
            FROM sq_command c
            JOIN sq_runtime_binding b ON b.user_id = c.user_id
                AND b.binding_id = c.binding_id
            WHERE c.user_id = #{userId}
              AND c.task_id = #{taskId}
              AND b.role = 'MASTER'
              AND c.action IN ('RESUME', 'RESPOND')
              AND c.status IN ('PENDING', 'DELIVERING')
            FOR UPDATE
            """)
    List<String> pendingControlIds(@Param("userId") String userId, @Param("taskId") String taskId);

    default int pendingControls(String userId, String taskId) {
        return pendingControlIds(userId, taskId).size();
    }
}
