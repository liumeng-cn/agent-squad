package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 回复冻结时观察到的安全故障事实。
 * <p>
 * 仅含同用户、同会话且来源消息不晚于本次消息的已提交事实，不携带内部异常或原始推理。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ResponseFailureFactBO {
    /** 故障分类 */
    private String category;
    /** 处理责任[SYSTEM,USER,OPERATOR] */
    private String actor;
    /** 面向用户的安全说明 */
    private String safeMessage;
    /** 来源消息标识 */
    private String sourceMessageId;
    /** 目标业务标识 */
    private String taskId;
}
