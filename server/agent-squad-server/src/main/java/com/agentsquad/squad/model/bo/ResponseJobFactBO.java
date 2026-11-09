package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 回复可读取的节点进度事实。
 * <p>
 * 只保存当前目标计划的节点身份与状态，不携带Worker日志、内部推理或未核验候选正文。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ResponseJobFactBO {
    /** 计划内节点键 */
    private String nodeKey;
    /** BLOCKED/READY/RUNNING/SUCCEEDED/PARTIAL/FAILED/WAITING_INPUT/WAITING_APPROVAL/RECOVERY_BLOCKED/RECONCILING/CANCELLING/CANCELLED/SUPERSEDED */
    private String status;
}
