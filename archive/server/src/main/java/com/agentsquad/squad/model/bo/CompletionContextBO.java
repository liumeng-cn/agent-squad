package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Master 完成候选提交上下文。
 * <p>
 * 由受控会话绑定和目标当前版本构成，模型不能自行指定归属、计划版本或答案版本。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CompletionContextBO {
    /**
     * 资源归属用户ID
     */
    private Long userId;
    /**
     * 目标业务ID
     */
    private Long taskId;
    /**
     * 原生会话绑定标识
     */
    private Long bindingId;
    /**
     * 目标任务状态
     */
    private String taskStatus;
    /**
     * 计划版本
     */
    private long planVersion;
    /**
     * 用户可见最终答案版本
     */
    private long answerRevision;
    /**
     * 候选来源原生TurnID
     */
    private String proposalTurnId;
    /**
     * 候选内容摘要，用于重放校验
     */
    private String proposalHash;
    /**
     * 候选来源工具调用ID
     */
    private String proposalToolCallId;
    /**
     * 完成候选内容（JSON）
     */
    private String proposalJson;
    /**
     * 是否已请求取消
     */
    private boolean cancellationRequested;
}
