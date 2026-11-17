package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.enums.PlanAcceptanceVerdict;

import com.agentsquad.squad.enums.PlanJoinPolicy;

import java.util.List;

/**
 * 计划完成判断所需事实
 * <p>
 * 应用服务在事务内读取当前计划、工作、待核对效果和必要验收结论；可交付结果必须来自已保存内容。
 */
public record PlanJoinFactsBO(long planVersion, PlanJoinPolicy policy, List<PlanJoinJobBO> jobs,
                              boolean pendingEffects, List<PlanAcceptanceVerdict> requiredAcceptance,
                              boolean hasDeliverable) {
}
