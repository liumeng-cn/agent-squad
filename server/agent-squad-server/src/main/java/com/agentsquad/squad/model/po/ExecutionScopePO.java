package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 执行归属数据库记录
 * <p>
 * 固定用户、会话代际与预算账户；ownerId 按 RESPONSE/TASK 归属校验。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_execution_scope")
public class ExecutionScopePO {
    /** 执行作用域业务ID */
    private String scopeId;
    /** 资源归属用户ID */
    private String userId;
    /** 所属会话ID */
    private String conversationId;
    /** 请求受理时的会话版本号 */
    private long conversationGeneration;
    /** 归属类型[RESPONSE,TASK] */
    private String kind;
    /** 回复执行或目标业务ID */
    private String ownerId;
    /** 共享预算账户ID */
    private String accountId;
}
