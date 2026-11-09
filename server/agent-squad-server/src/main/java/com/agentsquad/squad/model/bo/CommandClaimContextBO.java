package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 持久命令领取时的服务端归属。
 * <p>
 * 用于先锁会话和执行所有者，再领取命令；不从用户请求接受Scope或绑定身份。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CommandClaimContextBO {
    /** 资源归属用户ID */
    private String userId;
    /** 统一执行作用域ID */
    private String scopeId;
    /** 命令动作 */
    private String action;
    /** 原生会话绑定ID；删除类命令可为空 */
    private String bindingId;
}
