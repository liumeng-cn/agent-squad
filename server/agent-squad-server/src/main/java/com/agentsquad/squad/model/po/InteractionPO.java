package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 持久交互的存储记录；APPROVAL 绑定操作账本，INPUT 只绑定原生请求
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_interaction")
public class InteractionPO {
    /** 交互ID */
    private String interactionId;
    /** 资源归属用户ID */
    private String userId;
    /** 可选所属目标ID；普通回复为空 */
    private String taskId;
    /** 节点ID */
    private String jobId;
    /** 所属绑定 */
    private String bindingId;
    /** 关联操作 */
    private String operationId;
    /** BUSINESS/FRAMEWORK */
    private String origin;
    /** 真实原生请求ID；业务交互为空 */
    private String frameworkRequestId;
    /** INPUT/APPROVAL */
    private String kind;
    /** 用户问题 */
    private String question;
    /** 回复JSON Schema */
    private String answerSchemaJson;
    /** 绑定计划版本 */
    private long planVersion;
    /** 绑定请求摘要 */
    private String requestHash;
    /** 确认连接版本 */
    private long connectionRevision;
    /** 答复有效期 */
    private LocalDateTime expiresAt;
    /** OPEN/RESPONDED/EXPIRED/INVALIDATED/CANCELLED */
    private String status;
    /** 乐观版本 */
    private long version;
    /** 受Schema校验的用户回复 */
    private String responseJson;
    /** 统一执行作用域ID */
    private String scopeId;
}
