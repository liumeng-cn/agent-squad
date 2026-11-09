package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 修订协调扫描身份。
 * <p>
 * 扫描仅传递用户和目标身份，详细内容在会话与目标锁下读取。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TaskDirectiveTargetBO {
    /**
     * 目标修订指令业务ID
     */
    private String directiveId;
    /**
     * 归属用户ID
     */
    private String userId;
    /**
     * 当前受权目标ID
     */
    private String taskId;
}
