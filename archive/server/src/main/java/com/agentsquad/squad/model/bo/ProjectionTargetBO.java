package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 有已投递命令的固定原生会话及当前数据库记录水位
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionTargetBO {
    /**
     * 资源归属用户ID
     */
    private Long userId;
    /**
     * 可选所属目标ID；普通回复为空
     */
    private Long taskId;
    /**
     * 绑定业务ID
     */
    private Long bindingId;
    /**
     * 固定能力版本标识
     */
    private String agentId;
    /**
     * 原生会话标识
     */
    private String sessionId;
    /**
     * 事件读取游标
     */
    private long cursor;
}
