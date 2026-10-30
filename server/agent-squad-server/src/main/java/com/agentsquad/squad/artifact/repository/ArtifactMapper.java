package com.agentsquad.squad.artifact.repository;

import com.agentsquad.squad.artifact.domain.ArtifactRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产物元数据与内容的持久边界。
 * <p>
 * 所有读取按服务端用户归属过滤；目标附件同时受会话删除屏障限制。
 */
@Mapper
public interface ArtifactMapper {
    int insert(@Param("id") String id, @Param("userId") String userId, @Param("name") String name,
               @Param("mediaType") String mediaType, @Param("size") long size, @Param("hash") String hash,
               @Param("key") String key, @Param("content") byte[] content, @Param("expiresAt") LocalDateTime expiresAt);

    ArtifactRow findOwned(@Param("userId") String userId, @Param("id") String id);

    ArtifactRow findContent(@Param("userId") String userId, @Param("id") String id);

    ArtifactRow findUpload(@Param("userId") String userId, @Param("key") String key);

    int attach(@Param("userId") String userId, @Param("taskId") String taskId, @Param("id") String id);

    List<String> listTaskIds(@Param("userId") String userId, @Param("taskId") String taskId);

    int attachScope(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("id") String id);

    List<String> listScopeIds(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int attachMessage(@Param("userId") String userId, @Param("messageId") String messageId, @Param("id") String id);
}
