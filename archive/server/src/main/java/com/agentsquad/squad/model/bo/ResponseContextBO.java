package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 回复上下文冻结点。
 * <p>
 * 从固定绑定解析来源消息和已有快照，不接受模型自报会话或消息范围。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ResponseContextBO {
    /**
     * 回复执行业务ID
     */
    private Long executionId;
    /**
     * 来源用户消息ID
     */
    private Long sourceMessageId;
    /**
     * 来源执行序号
     */
    private long sourceSequence;
    /**
     * 冻结的授权会话消息前缀
     */
    private String contextJson;
    /**
     * 冻结前缀SHA256摘要
     */
    private String contextHash;
}
