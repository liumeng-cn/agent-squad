package com.agentsquad.squad.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 目标语义修订事实。
 * <p>
 * 与运行版本分开读取，防止单纯的等待和恢复变化使用户修改失效。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TaskGoalStateBO {
    /** 已应用目标语义修订号 */
    private long goalRevision;
    /** 规范化用户输入 */
    private String inputJson;
}
