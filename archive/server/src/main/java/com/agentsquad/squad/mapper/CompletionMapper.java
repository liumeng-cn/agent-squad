package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.ProjectionTargetBO;
import com.agentsquad.squad.model.po.TaskPO;
import com.agentsquad.squad.model.bo.CompletionContextBO;
import com.agentsquad.squad.model.bo.FinalizationBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Master 完成候选和最终状态裁决的
 */
@Mapper
public interface CompletionMapper {

    int countPendingEffects(@Param("userId") Long userId, @Param("taskId") Long taskId);

    CompletionContextBO lockByMasterSession(@Param("userId") Long userId,
                                            @Param("sessionId") String sessionId);

    int saveProposal(@Param("userId") Long userId, @Param("taskId") Long taskId,
                     @Param("bindingId") Long bindingId, @Param("turnId") String turnId,
                     @Param("toolCallId") String toolCallId, @Param("proposalHash") String proposalHash,
                     @Param("proposalJson") String proposalJson);

    FinalizationBO lockTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int completeTask(@Param("userId") Long userId, @Param("taskId") Long taskId,
                     @Param("bindingId") Long bindingId, @Param("turnId") String turnId,
                     @Param("outcome") String outcome, @Param("planVersion") long planVersion,
                     @Param("answerRevision") long answerRevision);

    java.util.List<ProjectionTargetBO> cancellationTargets(
            @Param("userId") Long userId, @Param("taskId") Long taskId);

    java.util.List<TaskPO> cancellingTasks(@Param("afterId") long afterId);

    java.util.List<Long> lockCancellationOperations(@Param("userId") Long userId, @Param("taskId") Long taskId);

    java.util.List<Long> lockCancellationSandboxes(@Param("userId") Long userId, @Param("taskId") Long taskId);

    java.util.List<Long> lockCancellationInteractions(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int cancelJobs(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int cancelTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int markCorrectionRequested(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int failWithoutProposal(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int blockForRecovery(@Param("userId") Long userId, @Param("taskId") Long taskId,
                         @Param("reason") String reason);

    int waitForInteraction(@Param("userId") Long userId, @Param("taskId") Long taskId,
                           @Param("status") String status, @Param("reason") String reason);

    int resumeFromInteraction(@Param("userId") Long userId, @Param("taskId") Long taskId);
}
