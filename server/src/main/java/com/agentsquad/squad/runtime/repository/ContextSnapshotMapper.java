package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.ContextPreparationRow;
import com.agentsquad.squad.runtime.domain.ContextSnapshotRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 局部上下文快照的数据库边界。
 *
 * 准备入口只读取受控 Master 绑定的用户目标；快照按用户和目标复合归属查找。
 */
@Mapper
public interface ContextSnapshotMapper {

    @ConstructorArgs({
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "input_json", javaType = String.class),
            @Arg(column = "task_status", javaType = String.class),
            @Arg(column = "cancellation_requested", javaType = boolean.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "conversation_status", javaType = String.class)
    })
    @Select("""
            SELECT b.user_id, b.task_id, t.input_json, t.status AS task_status,
                   (t.cancel_requested_at IS NOT NULL) AS cancellation_requested,
                   c.generation AS conversation_generation, c.status AS conversation_status
            FROM sq_runtime_binding b
            JOIN sq_goal_task t ON t.user_id = b.user_id AND t.task_id = b.task_id
            JOIN sq_conversation c ON c.user_id = t.user_id AND c.conversation_id = t.conversation_id
            WHERE b.user_id = #{userId} AND b.framework_session_id = #{sessionId}
              AND b.role = 'MASTER' AND c.active_task_id = t.task_id
            FOR UPDATE
            """)
    ContextPreparationRow lockPreparation(@Param("userId") String userId,
                                          @Param("sessionId") String sessionId);

    @ConstructorArgs({
            @Arg(column = "snapshot_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "target_node_key", javaType = String.class),
            @Arg(column = "logical_key", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "memory_revision", javaType = long.class),
            @Arg(column = "items_json", javaType = String.class),
            @Arg(column = "token_estimate", javaType = long.class),
            @Arg(column = "content_hash", javaType = String.class)
    })
    @Select("""
            SELECT snapshot_id, user_id, task_id, target_node_key, logical_key,
                   conversation_generation, memory_revision, items_json, token_estimate, content_hash
            FROM sq_context_snapshot
            WHERE user_id = #{userId} AND task_id = #{taskId}
              AND target_node_key = #{targetNodeKey} AND logical_key = #{logicalKey}
              AND del_flag = '0'
            """)
    ContextSnapshotRow findByLogicalKey(@Param("userId") String userId,
                                        @Param("taskId") String taskId,
                                        @Param("targetNodeKey") String targetNodeKey,
                                        @Param("logicalKey") String logicalKey);

    @ConstructorArgs({
            @Arg(column = "snapshot_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "target_node_key", javaType = String.class),
            @Arg(column = "logical_key", javaType = String.class),
            @Arg(column = "conversation_generation", javaType = long.class),
            @Arg(column = "memory_revision", javaType = long.class),
            @Arg(column = "items_json", javaType = String.class),
            @Arg(column = "token_estimate", javaType = long.class),
            @Arg(column = "content_hash", javaType = String.class)
    })
    @Select("""
            SELECT snapshot_id, user_id, task_id, target_node_key, logical_key,
                   conversation_generation, memory_revision, items_json, token_estimate, content_hash
            FROM sq_context_snapshot
            WHERE user_id = #{userId} AND task_id = #{taskId} AND snapshot_id = #{snapshotId}
              AND del_flag = '0'
            """)
    ContextSnapshotRow findOwned(@Param("userId") String userId, @Param("taskId") String taskId,
                                 @Param("snapshotId") String snapshotId);

    @Insert("""
            INSERT INTO sq_context_snapshot
                (snapshot_id, user_id, task_id, target_node_key, logical_key,
                 conversation_generation, memory_revision, items_json, token_estimate, content_hash, scope_id,
                 creator_id, creator_name, last_updater_id, last_updater_name)
            VALUES
                (#{snapshotId}, #{userId}, #{taskId}, #{targetNodeKey}, #{logicalKey},
                 #{conversationGeneration}, 0, #{itemsJson}, #{tokenEstimate}, #{contentHash},
                 (SELECT scope_id FROM sq_goal_task WHERE user_id = #{userId} AND task_id = #{taskId}),
                 #{userId}, '', #{userId}, '')
            """)
    int insert(@Param("snapshotId") String snapshotId, @Param("userId") String userId,
               @Param("taskId") String taskId, @Param("targetNodeKey") String targetNodeKey,
               @Param("logicalKey") String logicalKey,
               @Param("conversationGeneration") long conversationGeneration,
               @Param("itemsJson") String itemsJson, @Param("tokenEstimate") long tokenEstimate,
               @Param("contentHash") String contentHash);
}
