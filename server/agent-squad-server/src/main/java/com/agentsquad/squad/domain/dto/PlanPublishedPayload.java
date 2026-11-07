package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.common.enums.worker.PlanJoinPolicy;

import java.util.List;

/**
 * 会话内公开的计划安排
 * <p>
 * 只携带计划版本、工作编号和完成条件，不复制工作输入、上下文或模型原始内容。
 */
public record PlanPublishedPayload(int schemaVersion, String taskId, long planVersion, List<String> jobIds,
                                   PlanJoinPolicy joinPolicy, List<PlanAcceptanceCriterion> acceptanceCriteria) {
}
