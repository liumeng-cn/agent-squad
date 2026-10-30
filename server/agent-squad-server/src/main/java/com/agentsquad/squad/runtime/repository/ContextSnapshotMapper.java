package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.ContextPreparationRow;
import com.agentsquad.squad.runtime.domain.ContextSnapshotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 局部上下文快照的数据库边界。
 *
 * 准备入口只读取受控 Master 绑定的用户目标；快照按用户和目标复合归属查找。
 */
@Mapper
public interface ContextSnapshotMapper {

    ContextPreparationRow lockPreparation(@Param("userId") String userId,
                                          @Param("sessionId") String sessionId);

    ContextSnapshotRow findByLogicalKey(@Param("userId") String userId,
                                        @Param("taskId") String taskId,
                                        @Param("targetNodeKey") String targetNodeKey,
                                        @Param("logicalKey") String logicalKey);

    ContextSnapshotRow findOwned(@Param("userId") String userId, @Param("taskId") String taskId,
                                 @Param("snapshotId") String snapshotId);

    int insert(@Param("snapshotId") String snapshotId, @Param("userId") String userId,
               @Param("taskId") String taskId, @Param("targetNodeKey") String targetNodeKey,
               @Param("logicalKey") String logicalKey,
               @Param("conversationGeneration") long conversationGeneration,
               @Param("itemsJson") String itemsJson, @Param("tokenEstimate") long tokenEstimate,
               @Param("contentHash") String contentHash, @Param("goalRevision") long goalRevision,
               @Param("memoryRevision") long memoryRevision);
}
