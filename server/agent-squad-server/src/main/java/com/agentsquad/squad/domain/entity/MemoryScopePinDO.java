package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 执行记忆冻结点。
 * <p>
 * 同一Scope只冻结一次；纠正或删除后旧冻结点不能原位更新。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MemoryScopePinDO {
    /** 受权执行作用域业务ID */
    private String scopeId;
    /** 上下文固定时的用户记忆版本号 */
    private long memoryRevision;
    /** 上下文可用状态[CURRENT-可用,BLOCKED-禁止使用] */
    private String barrierStatus;
}
