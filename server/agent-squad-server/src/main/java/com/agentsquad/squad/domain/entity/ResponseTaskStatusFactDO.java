package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 回复冻结时观察到的最近目标状态事实。
 * <p>
 * 仅含同用户、同会话且来源消息不晚于本次消息的已提交事实，不携带内部异常或原始推理。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ResponseTaskStatusFactDO {
    /** 目标业务ID */
    private String taskId;
    /** 来源消息标识 */
    private String sourceMessageId;
    /** 产品状态 */
    private String status;
    /** 业务版本 */
    private long version;
    /** 已应用目标语义修订号 */
    private long goalRevision;
}
