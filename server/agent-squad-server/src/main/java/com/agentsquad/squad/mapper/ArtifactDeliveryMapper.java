package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.ArtifactStagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 隔离产物暂存与发布关联的
 * <p>
 * 暂存内容不通过下载入口公开，提交后记录唯一已发布产物ID。
 */
@Mapper
public interface ArtifactDeliveryMapper {
    int insert(@Param("stageId") Long stageId, @Param("userId") Long userId,
               @Param("scopeId") Long scopeId, @Param("bindingId") Long bindingId,
               @Param("executionId") Long executionId, @Param("workspaceKey") String workspaceKey,
               @Param("fileName") String fileName, @Param("hash") String hash, @Param("content") byte[] content,
               @Param("expiresAt") java.time.LocalDateTime expiresAt);

    ArtifactStagePO find(@Param("userId") Long userId, @Param("stageId") Long stageId);

    ArtifactStagePO lock(@Param("userId") Long userId, @Param("stageId") Long stageId);

    ArtifactStagePO findPath(@Param("userId") Long userId, @Param("executionId") Long executionId,
                             @Param("workspaceKey") String workspaceKey);

    int commit(@Param("userId") Long userId, @Param("stageId") Long stageId,
               @Param("artifactId") Long artifactId);

    int expireArtifacts();

    int clearScopeReferences();

    int clearTaskReferences();

    int clearMessageReferences();

    int purgeContents();

    int purgeStages();
}
