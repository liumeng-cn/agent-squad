package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import com.agentsquad.squad.domain.entity.TaskDO;
import com.agentsquad.squad.domain.entity.CompletionContextDO;
import com.agentsquad.squad.domain.entity.FinalizationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Master 完成候选和最终状态裁决的 
 */
@Mapper
public interface CompletionMapper {

    int countPendingEffects(@Param("userId") String userId, @Param("taskId") String taskId);

    CompletionContextDO lockByMasterSession(@Param("userId") String userId,
                                             @Param("sessionId") String sessionId);

    int saveProposal(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("turnId") String turnId,
                     @Param("toolCallId") String toolCallId, @Param("proposalHash") String proposalHash,
                     @Param("proposalJson") String proposalJson);

    FinalizationDO lockTask(@Param("userId") String userId, @Param("taskId") String taskId);

    int completeTask(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("turnId") String turnId,
                     @Param("outcome") String outcome, @Param("planVersion") long planVersion,
                     @Param("answerRevision") long answerRevision);

    java.util.List<ProjectionTargetDO> cancellationTargets(
            @Param("userId") String userId, @Param("taskId") String taskId);

    java.util.List<TaskDO> cancellingTasks(@Param("afterId") long afterId);

    java.util.List<String> lockCancellationOperations(@Param("userId") String userId, @Param("taskId") String taskId);

    java.util.List<String> lockCancellationSandboxes(@Param("userId") String userId, @Param("taskId") String taskId);

    java.util.List<String> lockCancellationInteractions(@Param("userId") String userId, @Param("taskId") String taskId);

    int cancelJobs(@Param("userId") String userId, @Param("taskId") String taskId);

    int cancelTask(@Param("userId") String userId, @Param("taskId") String taskId);

    int markCorrectionRequested(@Param("userId") String userId, @Param("taskId") String taskId);

    int failWithoutProposal(@Param("userId") String userId, @Param("taskId") String taskId);

    int blockForRecovery(@Param("userId") String userId, @Param("taskId") String taskId,
                         @Param("reason") String reason);

    int waitForInteraction(@Param("userId") String userId, @Param("taskId") String taskId,
                           @Param("status") String status, @Param("reason") String reason);

    int resumeFromInteraction(@Param("userId") String userId, @Param("taskId") String taskId);
}
