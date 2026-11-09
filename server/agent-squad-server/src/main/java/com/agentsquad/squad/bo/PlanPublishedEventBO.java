package com.agentsquad.squad.bo;

import com.agentsquad.squad.enums.PlanJoinPolicy;

import java.util.List;

/**
 * 计划发布的任务事件
 *
 * <p>保留任务事件流使用的计划版本、工作编号和验收条件。</p>
 */
public record PlanPublishedEventBO(long planVersion, List<String> jobIds, PlanJoinPolicy joinPolicy,
                                   List<PlanAcceptanceCriterionBO> acceptanceCriteria) {
}
