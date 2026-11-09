package com.agentsquad.squad.po;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 原生运行容量占位。
 *
 * 一条 Binding 同时最多持有一个活动占位；命令与原生 Turn 是持久归属，
 * 恢复命令须有后续新原生 Run 身份，不能凭旧 Turn 判定回收。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeAdmissionSlotPO {
    /** 归属用户标识 */
    private String userId;
    /** 固定原生会话绑定标识 */
    private String bindingId;
    /** 持有占位的原始命令标识 */
    private String commandId;
    /** 原始命令动作 */
    private String action;
    /** 原生执行回合ID */
    private String turnId;
    /** 恢复命令的新原生Run标识 */
    private String runId;
    /** 领取命令时已同步到业务记录的 AgentScope 事件序号 */
    private long claimCursor;
    /** ACTIVE或RELEASED */
    private String status;

    /**
     * 是否为活跃准入槽位
     */
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
