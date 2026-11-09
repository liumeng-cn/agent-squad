package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.ArtifactPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产物元数据与内容的
 * <p>
 * 所有读取按服务端用户归属过滤；目标附件同时受会话删除屏障限制。
 */
@Mapper
public interface ArtifactMapper {
    int insert(@Param("id") Long id, @Param("userId") Long userId, @Param("name") String name,
               @Param("mediaType") String mediaType, @Param("size") long size, @Param("hash") String hash,
               @Param("key") String key, @Param("content") byte[] content, @Param("expiresAt") LocalDateTime expiresAt);

    ArtifactPO findOwned(@Param("userId") Long userId, @Param("id") Long id);

    ArtifactPO findContent(@Param("userId") Long userId, @Param("id") Long id);

    ArtifactPO findUpload(@Param("userId") Long userId, @Param("key") String key);

    int attach(@Param("userId") Long userId, @Param("taskId") Long taskId, @Param("id") Long id);

    List<Long> listTaskIds(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int attachScope(@Param("userId") Long userId, @Param("scopeId") Long scopeId, @Param("id") Long id);

    List<Long> listScopeIds(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int attachMessage(@Param("userId") Long userId, @Param("messageId") Long messageId, @Param("id") Long id);
}
