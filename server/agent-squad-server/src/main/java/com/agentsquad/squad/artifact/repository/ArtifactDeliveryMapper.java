package com.agentsquad.squad.artifact.repository;

import com.agentsquad.squad.artifact.domain.ArtifactStageRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 隔离产物暂存与发布关联的持久边界。
 * <p>
 * 暂存内容不通过下载入口公开，提交后记录唯一已发布产物ID。
 */
@Mapper
public interface ArtifactDeliveryMapper {
    @Insert("""
            INSERT INTO sq_artifact_stage
                (stage_id, user_id, scope_id, binding_id, execution_id, workspace_key, file_name,
                 sha256, content_bytes, status, expires_at, creator_id, last_updater_id)
            VALUES
                (#{stageId}, #{userId}, #{scopeId}, #{bindingId}, #{executionId}, #{workspaceKey}, #{fileName},
                 #{hash}, #{content}, 'STAGED', #{expiresAt}, #{userId}, #{userId})
            """)
    int insert(@Param("stageId") String stageId, @Param("userId") String userId,
               @Param("scopeId") String scopeId, @Param("bindingId") String bindingId,
               @Param("executionId") String executionId, @Param("workspaceKey") String workspaceKey,
               @Param("fileName") String fileName, @Param("hash") String hash, @Param("content") byte[] content,
               @Param("expiresAt") java.time.LocalDateTime expiresAt);

    @Select("""
            SELECT stage_id, user_id, scope_id, binding_id, execution_id, workspace_key, file_name,
                   sha256, content_bytes AS content, status, artifact_id
            FROM sq_artifact_stage WHERE user_id = #{userId} AND stage_id = #{stageId}
              AND expires_at > CURRENT_TIMESTAMP AND del_flag = '0'
            """)
    ArtifactStageRow find(@Param("userId") String userId, @Param("stageId") String stageId);

    @Select("""
            SELECT stage_id, user_id, scope_id, binding_id, execution_id, workspace_key, file_name,
                   sha256, content_bytes AS content, status, artifact_id
            FROM sq_artifact_stage WHERE user_id = #{userId} AND stage_id = #{stageId}
              AND expires_at > CURRENT_TIMESTAMP AND del_flag = '0' FOR UPDATE
            """)
    ArtifactStageRow lock(@Param("userId") String userId, @Param("stageId") String stageId);

    @Select("""
            SELECT stage_id, user_id, scope_id, binding_id, execution_id, workspace_key, file_name,
                   sha256, content_bytes AS content, status, artifact_id
            FROM sq_artifact_stage WHERE user_id = #{userId} AND execution_id = #{executionId}
              AND workspace_key = #{workspaceKey} AND del_flag = '0'
            """)
    ArtifactStageRow findPath(@Param("userId") String userId, @Param("executionId") String executionId,
                              @Param("workspaceKey") String workspaceKey);

    @Update("""
            UPDATE sq_artifact_stage SET status = 'COMMITTED', artifact_id = #{artifactId},
                content_bytes = X'', last_update_time = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND stage_id = #{stageId} AND status = 'STAGED'
              AND expires_at > CURRENT_TIMESTAMP AND del_flag = '0'
            """)
    int commit(@Param("userId") String userId, @Param("stageId") String stageId,
               @Param("artifactId") String artifactId);

    @Update("""
            UPDATE sq_artifact SET del_flag = '1', last_update_time = CURRENT_TIMESTAMP
            WHERE expires_at <= CURRENT_TIMESTAMP AND del_flag = '0'
            """)
    int expireArtifacts();

    @Update("""
            UPDATE sq_scope_artifact SET del_flag = '1'
            WHERE EXISTS (SELECT 1 FROM sq_artifact a WHERE a.user_id = sq_scope_artifact.user_id
                AND a.artifact_id = sq_scope_artifact.artifact_id AND a.del_flag = '1')
            """)
    int clearScopeReferences();

    @Update("""
            UPDATE sq_task_artifact SET del_flag = '1'
            WHERE EXISTS (SELECT 1 FROM sq_artifact a WHERE a.user_id = sq_task_artifact.user_id
                AND a.artifact_id = sq_task_artifact.artifact_id AND a.del_flag = '1')
            """)
    int clearTaskReferences();

    @Update("""
            UPDATE sq_message_artifact SET del_flag = '1'
            WHERE EXISTS (SELECT 1 FROM sq_artifact a WHERE a.user_id = sq_message_artifact.user_id
                AND a.artifact_id = sq_message_artifact.artifact_id AND a.del_flag = '1')
            """)
    int clearMessageReferences();

    @Update("""
            UPDATE sq_artifact SET content_bytes = X''
            WHERE del_flag = '1' AND OCTET_LENGTH(content_bytes) > 0
              AND NOT EXISTS (SELECT 1 FROM sq_scope_artifact r WHERE r.user_id = sq_artifact.user_id
                  AND r.artifact_id = sq_artifact.artifact_id AND r.del_flag = '0')
              AND NOT EXISTS (SELECT 1 FROM sq_task_artifact r WHERE r.user_id = sq_artifact.user_id
                  AND r.artifact_id = sq_artifact.artifact_id AND r.del_flag = '0')
              AND NOT EXISTS (SELECT 1 FROM sq_message_artifact r WHERE r.user_id = sq_artifact.user_id
                  AND r.artifact_id = sq_artifact.artifact_id AND r.del_flag = '0')
            """)
    int purgeContents();

    @Update("""
            UPDATE sq_artifact_stage SET status = 'EXPIRED', del_flag = '1', content_bytes = X''
            WHERE expires_at <= CURRENT_TIMESTAMP AND status = 'STAGED'
            """)
    int purgeStages();
}
