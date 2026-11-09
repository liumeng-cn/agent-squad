package com.agentsquad.squad.model.bo;

import java.util.List;
import java.util.Collection;
import com.agentsquad.squad.enums.PlanJoinPolicy;

/**
 * Master 工具 publish_plan 的输入。
 * <p>
 * nodes 为不可变 DAG；joinPolicy 决定汇合标准，acceptanceCriteria 是完成条件。
 */
public record PlanPublishBO(String logicalKey, long expectedPlanVersion, List<PlanNodeBO> nodes,
                          PlanJoinPolicy joinPolicy, List<PlanAcceptanceCriterionBO> acceptanceCriteria) {

    public PlanPublishBO(String logicalKey, long expectedPlanVersion, List<PlanNodeBO> nodes,
                       String joinPolicy, List<PlanAcceptanceCriterionBO> acceptanceCriteria) {
        this(logicalKey, expectedPlanVersion, nodes, PlanJoinPolicy.valueOf(joinPolicy), acceptanceCriteria);
    }

    /** 旧调用的文字条件只转换结构，不产生已通过的检查结论。 */
    public PlanPublishBO(String logicalKey, long expectedPlanVersion, List<PlanNodeBO> nodes,
                       String joinPolicy, Collection<String> acceptanceCriteria) {
        this(logicalKey, expectedPlanVersion, nodes, PlanJoinPolicy.valueOf(joinPolicy),
                acceptanceCriteria == null ? null : acceptanceCriteria.stream().map(PlanAcceptanceCriterionBO::legacy).toList());
    }
}
