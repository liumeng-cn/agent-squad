package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 执行故障的数据库记录
 * <p>
 * 只保存稳定分类与安全说明，不存储异常原文、密钥或模型上下文。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_execution_failure")
public class ExecutionFailurePO {
    /** 故障业务ID */
    private String failureId;
    /** 受权执行作用域ID */
    private String scopeId;
    /** 故障来源原生绑定ID */
    private String bindingId;
    /** 故障分类 */
    private String category;
    /** 稳定原因码 */
    private String code;
    /** 处理责任[SYSTEM,USER,OPERATOR] */
    private String actor;
    /** 面向用户的安全说明 */
    private String safeMessage;
    /** 内部诊断关联ID，不含原始异常 */
    private String diagnosticId;
    /** 状态[ACTIVE,RETRYING,RESOLVED] */
    private String status;
    /** 该故障已批准的固定重试序号，避免并行执行改变计费身份 */
    private int retryAttempt;
}
