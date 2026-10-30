package com.agentsquad.squad.artifact.repository;

import com.agentsquad.squad.artifact.domain.ArtifactStageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 隔离产物暂存与发布关联的持久边界。
 * <p>
 * 暂存内容不通过下载入口公开，提交后记录唯一已发布产物ID。
 */
@Mapper
public interface ArtifactDeliveryMapper {
    int insert(@Param("stageId") String stageId, @Param("userId") String userId,
               @Param("scopeId") String scopeId, @Param("bindingId") String bindingId,
               @Param("executionId") String executionId, @Param("workspaceKey") String workspaceKey,
               @Param("fileName") String fileName, @Param("hash") String hash, @Param("content") byte[] content,
               @Param("expiresAt") java.time.LocalDateTime expiresAt);

    ArtifactStageRow find(@Param("userId") String userId, @Param("stageId") String stageId);

    ArtifactStageRow lock(@Param("userId") String userId, @Param("stageId") String stageId);

    ArtifactStageRow findPath(@Param("userId") String userId, @Param("executionId") String executionId,
                              @Param("workspaceKey") String workspaceKey);

    int commit(@Param("userId") String userId, @Param("stageId") String stageId,
               @Param("artifactId") String artifactId);

    int expireArtifacts();

    int clearScopeReferences();

    int clearTaskReferences();

    int clearMessageReferences();

    int purgeContents();

    int purgeStages();
}
