package com.agentsquad.squad.artifact;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产物元数据与内容的持久边界。
 * <p>
 * 所有读取按服务端用户归属过滤；目标附件同时受会话删除屏障限制。
 */
@Mapper
public interface ArtifactMapper {
    @Insert("""
            INSERT INTO sq_artifact (artifact_id, user_id, file_name, media_type, size_bytes,
                sha256, upload_key, content_bytes, expires_at)
            VALUES (#{id}, #{userId}, #{name}, #{mediaType}, #{size}, #{hash}, #{key}, #{content}, #{expiresAt})
            """)
    int insert(@Param("id") String id, @Param("userId") String userId, @Param("name") String name,
               @Param("mediaType") String mediaType, @Param("size") long size, @Param("hash") String hash,
               @Param("key") String key, @Param("content") byte[] content, @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT artifact_id, user_id, file_name, media_type, size_bytes, sha256, upload_key,
                   NULL AS content, expires_at FROM sq_artifact
            WHERE user_id = #{userId} AND artifact_id = #{id} AND del_flag = '0'
              AND expires_at > CURRENT_TIMESTAMP
              AND NOT EXISTS (SELECT 1 FROM sq_task_artifact r JOIN sq_goal_task t ON t.task_id = r.task_id
                  JOIN sq_conversation c ON c.conversation_id = t.conversation_id
                  WHERE r.artifact_id = sq_artifact.artifact_id AND c.status <> 'ACTIVE')
            """)
    ArtifactRow findOwned(@Param("userId") String userId, @Param("id") String id);

    @Select("""
            SELECT artifact_id, user_id, file_name, media_type, size_bytes, sha256, upload_key,
                   content_bytes AS content, expires_at FROM sq_artifact
            WHERE user_id = #{userId} AND artifact_id = #{id} AND del_flag = '0'
              AND expires_at > CURRENT_TIMESTAMP
              AND NOT EXISTS (SELECT 1 FROM sq_task_artifact r JOIN sq_goal_task t ON t.task_id = r.task_id
                  JOIN sq_conversation c ON c.conversation_id = t.conversation_id
                  WHERE r.artifact_id = sq_artifact.artifact_id AND c.status <> 'ACTIVE')
            """)
    ArtifactRow findContent(@Param("userId") String userId, @Param("id") String id);

    @Select("""
            SELECT artifact_id, user_id, file_name, media_type, size_bytes, sha256, upload_key,
                   NULL AS content, expires_at FROM sq_artifact
            WHERE user_id = #{userId} AND upload_key = #{key}
            """)
    ArtifactRow findUpload(@Param("userId") String userId, @Param("key") String key);

    @Insert("INSERT INTO sq_task_artifact (user_id, task_id, artifact_id) VALUES (#{userId}, #{taskId}, #{id})")
    int attach(@Param("userId") String userId, @Param("taskId") String taskId, @Param("id") String id);

    @Select("SELECT artifact_id FROM sq_task_artifact WHERE user_id = #{userId} AND task_id = #{taskId} ORDER BY id")
    List<String> listTaskIds(@Param("userId") String userId, @Param("taskId") String taskId);
}
