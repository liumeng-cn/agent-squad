package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 目标修订的数据库记录
 * <p>
 * 来源原文与Master归纳分开；指令终态不可原位复活，协调命令身份固定。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TaskDirectiveDO {
    /** 目标修订指令业务ID */
    private String directiveId;
    /** 归属用户ID */
    private String userId;
    /** 归属会话ID */
    private String conversationId;
    /** 会话代际 */
    private long generation;
    /** 受权用户消息ID */
    private String sourceMessageId;
    /** 来源回复执行作用域ID */
    private String sourceScopeId;
    /** 当前受权目标ID */
    private String taskId;
    /** 来源Scope内稳定业务调用键 */
    private String logicalKey;
    /** 规范化受理参数摘要 */
    private String payloadHash;
    /** 受理时校验的目标运行版本 */
    private long expectedVersion;
    /** 用户修改所基于的目标语义修订号 */
    private long expectedGoalRevision;
    /** 类型[SUPPLEMENT,REPLACE] */
    private String kind;
    /** Master归纳的修改建议，不冒充用户原话 */
    private String content;
    /** 受权用户消息的实际原文 */
    private String sourceText;
    /** 来源消息的受权附件引用 */
    private String artifactIdsJson;
    /** 状态[ACCEPTED,APPLIED,REJECTED] */
    private String status;
    /** 独立任务协调命令ID */
    private String commandId;
    /** 实际应用后的目标运行版本 */
    private Long appliedVersion;
    /** 实际应用后的目标语义修订号 */
    private Long appliedGoalRevision;
    /** 未应用的结构化原因码 */
    private String reasonCode;
    /** 可向用户解释的安全原因 */
    private String reasonMessage;
}
