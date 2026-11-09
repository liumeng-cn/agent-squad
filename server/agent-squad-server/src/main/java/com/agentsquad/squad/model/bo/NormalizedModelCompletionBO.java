package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.enums.ModelCompletionStatus;
import com.agentsquad.squad.enums.ModelEvidenceIssue;
import com.agentsquad.squad.enums.ModelFinishReason;

/**
 * 模型结束事实
 * <p>
 * 停止原因和用量分别保存原生日志序号及证据缺口；调用方仍需校验原生回合、发布权和开放效果。
 */
public record NormalizedModelCompletionBO(String runId, String turnId, String modelCallId,
                                        ModelCompletionStatus status, ModelFinishReason finishReason,
                                        ModelEvidenceIssue completionIssue, Long reasonSequence, Long endSequence,
                                        ModelTokenUsageBO usage, ModelEvidenceIssue usageIssue, Long usageSequence) {
    public boolean hasConfirmedNormalStop() {
        return completionIssue == ModelEvidenceIssue.NONE
                && status == ModelCompletionStatus.COMPLETED && finishReason == ModelFinishReason.STOP;
    }

    public boolean hasTrustedUsage() {
        return usageIssue == ModelEvidenceIssue.NONE && usage != null;
    }
}
