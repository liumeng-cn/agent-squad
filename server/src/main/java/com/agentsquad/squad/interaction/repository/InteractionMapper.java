package com.agentsquad.squad.interaction.repository;

import com.agentsquad.squad.interaction.domain.InteractionRow;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久交互 SQL 边界；框架请求按 (user, binding, requestId) 唯一绑定，答复带版本 CAS。
 */
@Mapper
public interface InteractionMapper {

    @Insert("""
            INSERT INTO sq_interaction
                (interaction_id, user_id, task_id, job_id, binding_id, operation_id, origin,
                 framework_request_id, kind, question, answer_schema_json, plan_version,
                 request_hash, connection_revision, expires_at, status, version,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{interactionId}, #{userId}, #{taskId}, #{jobId}, #{bindingId}, #{operationId},
                 #{origin}, #{frameworkRequestId}, #{kind}, #{question}, #{answerSchemaJson},
                 #{planVersion}, #{requestHash}, #{connectionRevision}, #{expiresAt}, 'OPEN', 0,
                 #{creatorId}, #{creatorName}, #{creatorId}, #{creatorName})
            """)
    int insert(@Param("interactionId") String interactionId, @Param("userId") String userId,
               @Param("taskId") String taskId, @Param("jobId") String jobId,
               @Param("bindingId") String bindingId, @Param("operationId") String operationId,
               @Param("origin") String origin, @Param("frameworkRequestId") String frameworkRequestId,
               @Param("kind") String kind, @Param("question") String question,
               @Param("answerSchemaJson") String answerSchemaJson,
               @Param("planVersion") long planVersion, @Param("requestHash") String requestHash,
               @Param("connectionRevision") long connectionRevision,
               @Param("expiresAt") LocalDateTime expiresAt,
               @Param("creatorId") String creatorId, @Param("creatorName") String creatorName);

    @ConstructorArgs({
            @Arg(column = "interaction_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "origin", javaType = String.class),
            @Arg(column = "framework_request_id", javaType = String.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "question", javaType = String.class),
            @Arg(column = "answer_schema_json", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "response_json", javaType = String.class)
    })
    @Select("""
            SELECT interaction_id, user_id, task_id, job_id, binding_id, operation_id, origin,
                   framework_request_id, kind, question, answer_schema_json, plan_version,
                   request_hash, connection_revision, expires_at, status, version, response_json
            FROM sq_interaction
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND framework_request_id = #{frameworkRequestId} AND del_flag = '0'
            """)
    InteractionRow findByFrameworkRequest(@Param("userId") String userId,
                                          @Param("bindingId") String bindingId,
                                          @Param("frameworkRequestId") String frameworkRequestId);

    @ConstructorArgs({
            @Arg(column = "interaction_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "origin", javaType = String.class),
            @Arg(column = "framework_request_id", javaType = String.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "question", javaType = String.class),
            @Arg(column = "answer_schema_json", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "response_json", javaType = String.class)
    })
    @Select("""
            SELECT interaction_id, user_id, task_id, job_id, binding_id, operation_id, origin,
                   framework_request_id, kind, question, answer_schema_json, plan_version,
                   request_hash, connection_revision, expires_at, status, version, response_json
            FROM sq_interaction
            WHERE user_id = #{userId} AND interaction_id = #{interactionId} AND del_flag = '0'
            """)
    InteractionRow findOwned(@Param("userId") String userId,
                             @Param("interactionId") String interactionId);

    @ConstructorArgs({
            @Arg(column = "interaction_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "job_id", javaType = String.class),
            @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "operation_id", javaType = String.class),
            @Arg(column = "origin", javaType = String.class),
            @Arg(column = "framework_request_id", javaType = String.class),
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "question", javaType = String.class),
            @Arg(column = "answer_schema_json", javaType = String.class),
            @Arg(column = "plan_version", javaType = long.class),
            @Arg(column = "request_hash", javaType = String.class),
            @Arg(column = "connection_revision", javaType = long.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "version", javaType = long.class),
            @Arg(column = "response_json", javaType = String.class)
    })
    @Select("""
            <script>SELECT interaction_id, user_id, task_id, job_id, binding_id, operation_id, origin,
                   framework_request_id, kind, question, answer_schema_json, plan_version,
                   request_hash, connection_revision, expires_at, status, version, response_json
            FROM sq_interaction
            WHERE user_id = #{userId} AND task_id = #{taskId} AND del_flag = '0'
              <if test="cursorId != null">AND id &lt; #{cursorId}</if>
            ORDER BY id DESC
            LIMIT #{limit}</script>
            """)
    List<InteractionRow> listByTask(@Param("userId") String userId, @Param("taskId") String taskId,
                                    @Param("cursorId") Long cursorId, @Param("limit") int limit);

    @Select("""
            SELECT interaction_id FROM sq_interaction
            WHERE user_id = #{userId} AND task_id = #{taskId} AND status = 'OPEN'
            ORDER BY id ASC
            LIMIT 8
            """)
    List<String> findOpenIds(@Param("userId") String userId, @Param("taskId") String taskId);

    @Select("""
            SELECT COUNT(*) FROM sq_interaction
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND kind = #{kind} AND status = 'OPEN' AND del_flag = '0'
            """)
    int countOpenByKind(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("kind") String kind);

    @Select("""
            SELECT id FROM sq_interaction
            WHERE user_id = #{userId} AND interaction_id = #{interactionId} AND del_flag = '0'
            """)
    Long cursorIdOf(@Param("userId") String userId, @Param("interactionId") String interactionId);

    @Update("""
            UPDATE sq_interaction
            SET status = 'RESPONDED', response_json = #{responseJson},
                version = version + 1, last_updater_id = #{userId}, last_updater_name = #{updaterName}
            WHERE user_id = #{userId} AND interaction_id = #{interactionId}
              AND status = 'OPEN' AND version = #{expectedVersion}
            """)
    int markResponded(@Param("userId") String userId,
                      @Param("interactionId") String interactionId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("responseJson") String responseJson,
                      @Param("updaterName") String updaterName);

    @Update("""
            UPDATE sq_interaction
            SET status = 'RESPONDED', response_json = COALESCE(response_json, #{responseJson}),
                version = version + 1, last_updater_id = 'framework'
            WHERE user_id = #{userId} AND binding_id = #{bindingId}
              AND framework_request_id = #{frameworkRequestId} AND status = 'OPEN'
            """)
    int markResolvedByFramework(@Param("userId") String userId,
                                @Param("bindingId") String bindingId,
                                @Param("frameworkRequestId") String frameworkRequestId,
                                @Param("responseJson") String responseJson);
}
