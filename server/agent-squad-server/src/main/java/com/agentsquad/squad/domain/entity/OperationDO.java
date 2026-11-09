package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 外部操作的数据库查询结果；账本是副作用唯一权威，框架日志只是佐证
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class OperationDO {
    /** 操作ID */
    private String operationId;
    /** 资源归属用户ID */
    private String userId;
    /** 可选所属目标ID；普通回复为空 */
    private String taskId;
    /** 节点ID；Master可空 */
    private String jobId;
    /** 原生调用所属绑定 */
    private String bindingId;
    /** 原生TurnID */
    private String frameworkTurnId;
    /** 原生稳定工具调用ID */
    private String toolCallId;
    /** 工具键 */
    private String toolKey;
    /** 工具版本 */
    private String toolVersion;
    /** 业务写入幂等键；READ可空 */
    private String businessIntentKey;
    /** READ/WRITE/SANDBOX */
    private String effect;
    /** 用户连接ID */
    private String connectionId;
    /** 派发时连接版本 */
    private long connectionRevision;
    /** 派发时授权版本 */
    private long authVersion;
    /** 规范化参数摘要 */
    private String requestHash;
    /** 脱敏参数；敏感值用引用 */
    private String argumentsJson;
    /** PREPARED/WAITING_APPROVAL/IN_FLIGHT/UNKNOWN/RECONCILING/SUCCEEDED/FAILED/CANCELLED */
    private String status;
    /** 业务乐观版本 */
    private long version;
    /** 独立执行器实例 */
    private String executorOwner;
    /** 独立执行器栅栏 */
    private long executorFence;
    /** 外部幂等或查询关联号 */
    private String externalKey;
    /** 可信连接器返回结果或人工核对结果 */
    private String resultJson;
    /** 统一执行作用域ID */
    private String scopeId;
    /** 该操作固定的预算预留稳定键 */
    private String budgetUsageKey;
}
