package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 执行所有者的当前状态。
 * <p>
 * 用于共享派发屏障；回复与目标独立存储，仅统一读取状态、取消意图与逻辑删除。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionOwnerBO {
    /** 状态[STAGED,COMMITTED,EXPIRED] */
    private String status;
    /** 是否已请求取消 */
    private boolean cancellationRequested;
    /** 是否已删除 */
    private String deleted;
}
