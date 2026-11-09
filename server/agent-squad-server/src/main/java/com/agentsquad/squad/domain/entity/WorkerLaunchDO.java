package com.agentsquad.squad.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Worker 固定 Binding 对应的计划、节点与业务 guard 快照。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class WorkerLaunchDO {
    /** 资源归属用户ID */
    private String userId;
    /** 所属目标ID */
    private String taskId;
    /** 委派节点业务ID */
    private String jobId;
    /** 所属计划版本 */
    private long planVersion;
    /** 计划内节点键 */
    private String nodeKey;
    /** 能力键 */
    private String workerKey;
    /** 能力版本 */
    private String workerVersion;
    /** Worker 任务状态 */
    private String jobStatus;
    /** 计划状态 */
    private String planStatus;
    /** 运行时绑定ID */
    private String bindingId;
    /** 固定能力版本标识 */
    private String agentId;
    /** 固定局部输入 */
    private String inputJson;
    /** 取消屏障时间；新计划替代在途旧Job时写入 */
    private LocalDateTime cancelRequestedAt;
    /** 当前业务执行 guard 令牌 */
    private String guardToken;
    /** 当前业务 guard 到期时间 */
    private LocalDateTime guardLeaseUntil;
    /** 结果候选（非最终结果） */
    private String resultCandidateJson;
    /** 提交结果候选时的 guard 令牌 */
    private String candidateGuardToken;
    /** 提交结果候选的原生回合ID */
    private String candidateTurnId;
}
