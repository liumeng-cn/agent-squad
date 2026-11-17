package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 会话回复可读取的修订状态。
 * <p>
 * 保留来源消息与实际应用版本，不将受理状态表述成已应用。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TaskDirectiveFactBO {
    /**
     * 目标修订指令业务ID
     */
    private Long directiveId;
    /**
     * 受权用户消息ID
     */
    private Long sourceMessageId;
    /**
     * 类型[SUPPLEMENT,REPLACE]
     */
    private String kind;
    /**
     * 状态[ACCEPTED,APPLIED,REJECTED]
     */
    private String status;
    /**
     * 实际应用后的目标运行版本
     */
    private Long appliedVersion;
    /**
     * 目标修订版本
     */
    private Long goalRevision;
    /**
     * 未应用的结构化原因码
     */
    private String reasonCode;
    /**
     * 可向用户解释的安全原因
     */
    private String reasonMessage;
}
