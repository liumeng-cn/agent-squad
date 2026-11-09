package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 目标终态裁决快照。
 * <p>
 * 包含候选、取消屏障和纠正次数，供同一事务判断是否完成、取消或纠正。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class FinalizationDO {
    /** 资源归属用户ID */
    private String userId;
    /** 目标业务ID */
    private String taskId;
    /** 所属对话ID */
    private String conversationId;
    /** 产品状态 */
    private String status;
    /** 业务版本 */
    private long version;
    /** 计划版本 */
    private long planVersion;
    /** 用户可见最终答案版本 */
    private long answerRevision;
    /** 候选来源绑定ID */
    private String proposalBindingId;
    /** 候选来源原生TurnID */
    private String proposalTurnId;
    /** 完成候选内容（JSON） */
    private String proposalJson;
    /** 是否已请求取消 */
    private boolean cancellationRequested;
    /** 无效完成候选纠正次数 */
    private int correctionCount;
}
