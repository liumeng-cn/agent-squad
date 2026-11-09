package com.agentsquad.squad.mapper;

import com.agentsquad.squad.bo.ProjectionTargetBO;
import com.agentsquad.squad.po.TaskPO;
import com.agentsquad.squad.bo.CompletionContextBO;
import com.agentsquad.squad.bo.FinalizationBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Master 完成候选和最终状态裁决的 
 */
@Mapper
public interface CompletionMapper {

    int countPendingEffects(@Param("userId") String userId, @Param("taskId") String taskId);

    CompletionContextBO lockByMasterSession(@Param("userId") String userId,
                                             @Param("sessionId") String sessionId);

    int saveProposal(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("turnId") String turnId,
                     @Param("toolCallId") String toolCallId, @Param("proposalHash") String proposalHash,
                     @Param("proposalJson") String proposalJson);

    FinalizationBO lockTask(@Param("userId") String userId, @Param("taskId") String taskId);

    int completeTask(@Param("userId") String userId, @Param("taskId") String taskId,
                     @Param("bindingId") String bindingId, @Param("turnId") String turnId,
                     @Param("outcome") String outcome, @Param("planVersion") long planVersion,
                     @Param("answerRevision") long answerRevision);

    java.util.List<ProjectionTargetBO> cancellationTargets(
            @Param("userId") String userId, @Param("taskId") String taskId);

    java.util.List<TaskPO> cancellingTasks(@Param("afterId") long afterId);

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
