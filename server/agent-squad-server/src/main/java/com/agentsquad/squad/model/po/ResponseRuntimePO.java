package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.agentsquad.squad.enums.ResponseCompletionProtocol;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 回复生命周期状态记录。
 * <p>
 * 与绑定、来源和完成候选同读，原生成功前不将候选正文公开。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_response_execution")
public class ResponseRuntimePO {
    /**
     * 回复执行业务ID
     */
    private Long executionId;
    /**
     * 资源归属用户ID
     */
    private Long userId;
    /**
     * 所属会话ID
     */
    private Long conversationId;
    /**
     * 来源用户消息ID
     */
    private Long sourceMessageId;
    /**
     * 回复执行作用域ID
     */
    private Long scopeId;
    /**
     * 固定原生绑定ID
     */
    private Long bindingId;
    /**
     * 回复生命周期状态
     */
    private String status;
    /**
     * 业务乐观版本
     */
    private long version;
    /**
     * 是否已请求取消
     */
    private boolean cancellationRequested;
    /**
     * 完成候选内容（JSON）
     */
    private String proposalJson;
    /**
     * 完成候选来源原生TurnID
     */
    private String proposalTurnId;
    /**
     * 完成候选规范化摘要
     */
    private String proposalHash;
    /**
     * 无效完成候选纠正次数
     */
    private int correctionCount;
    /**
     * 已持久化的自动恢复次数
     */
    private int recoveryAttempts;
    /**
     * 授权助手回答消息ID
     */
    private Long answerMessageId;
    /**
     * 回复完成协议[PROPOSAL-旧候选,NATURAL-自然输出]
     */
    private ResponseCompletionProtocol completionProtocol;
}
