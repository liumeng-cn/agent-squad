package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 工具调用时的目标与绑定上下文；由平台锁定读取，模型不能指定。
 * Master 直连流程无 Worker 节点，操作账本中的节点 ID 记为空。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContextBO {
    /**
     * 所属目标ID；删除类命令可为空
     */
    private String taskId;
    /**
     * 原生会话绑定ID；删除类命令可为空
     */
    private String bindingId;
    /**
     * 目标任务状态
     */
    private String taskStatus;
    /**
     * 是否已请求取消
     */
    private boolean cancellationRequested;
    /**
     * 统一执行作用域ID
     */
    private String scopeId;
}
