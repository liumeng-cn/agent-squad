package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 局部上下文准备时的任务事实。
 * <p>
 * 由受控 Master 会话查得用户原始目标与对话代际，不接受模型声明的用户身份或材料来源。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ContextPreparationBO {
    /**
     * 资源归属用户ID
     */
    private String userId;
    /**
     * 所属目标ID
     */
    private String taskId;
    /**
     * 任务输入内容（JSON）
     */
    private String inputJson;
    /**
     * 目标任务状态
     */
    private String taskStatus;
    /**
     * 是否已请求取消
     */
    private boolean cancellationRequested;
    /**
     * 上下文固定时的对话版本号
     */
    private long conversationGeneration;
    /**
     * 会话状态
     */
    private String conversationStatus;
}
