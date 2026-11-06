package com.agentsquad.squad.runtime.domain;

import com.agentsquad.squad.common.enums.worker.PlanAcceptanceVerdict;

import com.agentsquad.squad.common.enums.worker.PlanJoinPolicy;

import java.util.List;

/**
 * 计划完成判断所需事实
 * <p>
 * 应用服务在事务内读取当前计划、工作、待核对效果和必要验收结论；可交付结果必须来自已保存内容。
 */
public record PlanJoinFacts(long planVersion, PlanJoinPolicy policy, List<PlanJoinJob> jobs,
                            boolean pendingEffects, List<PlanAcceptanceVerdict> requiredAcceptance,
                            boolean hasDeliverable) {
}
