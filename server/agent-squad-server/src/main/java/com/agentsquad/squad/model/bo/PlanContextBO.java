package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * publish_plan 的锁定上下文。
 * <p>
 * 由受控 Master 会话和目标当前版本构成；模型不能自行指定归属、计划版本或答案版本。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class PlanContextBO {
    /** 资源归属用户ID */
    private String userId;
    /** 可选所属目标ID；普通回复为空 */
    private String taskId;
    /** 所属绑定 */
    private String bindingId;
    /** 绑定计划版本 */
    private long planVersion;
    /** 目标任务状态 */
    private String taskStatus;
    /** 是否已请求取消 */
    private boolean cancellationRequested;
    /** 会话代际 */
    private long conversationGeneration;
    /** 会话状态 */
    private String conversationStatus;
}
