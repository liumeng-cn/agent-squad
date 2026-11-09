package com.agentsquad.squad.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 已领取命令的不可变投递快照；Binding 标识必须与命令记录一致。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryBO {
    /** 命令业务ID */
    private String commandId;
    /** 资源归属用户ID */
    private String userId;
    /** 所属目标ID；删除类命令可为空 */
    private String taskId;
    /** 原生会话绑定ID；删除类命令可为空 */
    private String bindingId;
    /** 命令动作 */
    private String action;
    /** 规范化命令输入 */
    private String payloadJson;
    /** 消费失败重试次数 */
    private int attempts;
    /** 固定能力版本标识 */
    private String agentId;
    /** 原生会话标识 */
    private String sessionId;
}
