package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.CompletionContextRow;
import com.agentsquad.squad.runtime.domain.FinalizationRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Master 完成候选和最终状态裁决的 SQL 边界。 */
@Mapper
public interface CompletionMapper {

    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "task_status", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "answer_revision", javaType = long.class),
            @Arg(column = "proposal_turn_id", javaType = String.class),
            @Arg(column = "proposal_hash", javaType = String.class),
            @Arg(column = "proposal_tool_call_id", javaType = String.class),
            @Arg(column = "completion_proposal_json", javaType = String.class),
            @Arg(column = "cancellation_requested", javaType = boolean.class)
    })
    @Select("""
            SELECT b.user_id, b.task_id, b.binding_id, t.status AS task_status,
                   t.plan_version, t.answer_revision,
                   t.proposal_turn_id, t.proposal_hash, t.proposal_tool_call_id,
                   t.completion_proposal_json,
                   (t.cancel_requested_at IS NOT NULL) AS cancellation_requested
            FROM sq_runtime_binding b
            JOIN sq_goal_task t ON t.user_id = b.user_id AND t.task_id = b.task_id
            WHERE b.user_id = #{userId} AND b.framework_session_id = #{sessionId}
              AND b.role = 'MASTER'
            FOR UPDATE
            """)
    CompletionContextRow lockByMasterSession(@Param("userId") String userId,
                                             @Param("sessionId") String sessionId);

    @Update("""
            UPDATE sq_goal_task SET completion_proposal_json = #{proposalJson},
                proposal_binding_id = #{bindingId}, proposal_turn_id = #{turnId},
                proposal_tool_call_id = #{toolCallId}, proposal_hash = #{proposalHash},
                version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND status IN ('QUEUED', 'RUNNING') AND cancel_requested_at IS NULL
              AND completion_proposal_json IS NULL
            """)
    int saveProposal(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("turnId") String turnId,
                     @Param("toolCallId") String toolCallId, @Param("proposalHash") String proposalHash,
                     @Param("proposalJson") String proposalJson);

    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "answer_revision", javaType = long.class),
            @Arg(column = "proposal_binding_id", javaType = String.class),
            @Arg(column = "proposal_turn_id", javaType = String.class),
            @Arg(column = "completion_proposal_json", javaType = String.class),
            @Arg(column = "cancellation_requested", javaType = boolean.class),
            @Arg(column = "completion_correction_count", javaType = int.class)
    })
    @Select("""
            SELECT user_id, task_id, conversation_id, status, version, plan_version, answer_revision,
                   proposal_binding_id,
                   proposal_turn_id, completion_proposal_json, completion_correction_count,
                   (cancel_requested_at IS NOT NULL) AS cancellation_requested
            FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}
            FOR UPDATE
            """)
    FinalizationRow lockTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_goal_task SET status = #{outcome}, answer_revision = answer_revision + 1,
                version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId} AND status = 'RUNNING'
              AND cancel_requested_at IS NULL AND proposal_binding_id = #{bindingId}
              AND proposal_turn_id = #{turnId} AND completion_proposal_json IS NOT NULL
              AND plan_version = #{planVersion} AND answer_revision = #{answerRevision}
            """)
    int completeTask(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("turnId") String turnId,
                     @Param("outcome") String outcome, @Param("planVersion") long planVersion,
                     @Param("answerRevision") long answerRevision);

    @Update("""
            UPDATE sq_goal_task SET status = 'CANCELLED', version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND cancel_requested_at IS NOT NULL
              AND status NOT IN ('SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')
            """)
    int cancelTask(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_goal_task
            SET completion_correction_count = 1, version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId} AND status = 'RUNNING'
              AND completion_correction_count = 0 AND completion_proposal_json IS NULL
              AND cancel_requested_at IS NULL
            """)
    int markCorrectionRequested(@Param("userId") String userId, @Param("taskId") String taskId);

    @Update("""
            UPDATE sq_goal_task
            SET status = 'FAILED', answer_revision = answer_revision + 1, version = version + 1
            WHERE user_id = #{userId} AND task_id = #{taskId} AND status = 'RUNNING'
              AND completion_correction_count = 1 AND completion_proposal_json IS NULL
              AND cancel_requested_at IS NULL
            """)
    int failWithoutProposal(@Param("userId") String userId, @Param("taskId") String taskId);
}
