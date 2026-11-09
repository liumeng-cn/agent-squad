package com.agentsquad.squad.po;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 协作计划的数据库记录
 * <p>
 * 计划头不可变；status 表示 ACTIVE 或被新版本 SUPERSEDED，signal_version 记录汇合推进水位。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class PlanPO {
    /** 存储主键ID */
    private long id;
    /** 计划业务ID */
    private String planId;
    /** 资源归属用户ID */
    private String userId;
    /** 所属目标ID */
    private String taskId;
    /** 计划版本（taskId 内递增） */
    private long planVersion;
    /** ACTIVE/SUPERSEDED */
    private String status;
    /** 计划稳定逻辑键（幂等重放） */
    private String logicalKey;
    /** 不可变计划定义：节点/依赖/汇合标准/能力与权限版本引用 */
    private String definitionJson;
    /** 汇合信号版本 */
    private long signalVersion;
    /** 业务版本 */
    private long version;
    /** 发布计划时的目标修订号 */
    private long goalRevision;
}
