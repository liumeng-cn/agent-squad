package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 原生会话绑定的数据库记录
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SandboxBindingBO {
    /**
     * 资源归属用户ID
     */
    private String userId;
    /**
     * 统一执行作用域ID
     */
    private String scopeId;
    /**
     * 绑定业务ID
     */
    private String bindingId;
    /**
     * 原生会话标识
     */
    private String sessionId;
    /**
     * MASTER/WORKER
     */
    private String role;
}
