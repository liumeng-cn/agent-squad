package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 沙箱执行账本投影。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SandboxRunDO {
    /** 沙箱执行业务ID */
    private String executionId;
    /** 归属用户ID */
    private String userId;
    /** 执行作用域ID */
    private String scopeId;
    /** 固定原生绑定ID */
    private String bindingId;
    /** 固定请求SHA256摘要 */
    private String requestHash;
    /** 执行状态[IN_FLIGHT,SUCCEEDED,FAILED,UNKNOWN] */
    private String status;
    /** 已知执行结果与暂存产物引用 */
    private String resultJson;
}
