package com.agentsquad.squad.domain.entity;

import com.agentsquad.squad.domain.enums.ActivityGroup;
import com.agentsquad.squad.domain.enums.ActivityStatus;
import com.agentsquad.squad.domain.enums.ActivityType;
import com.agentsquad.squad.domain.enums.ActivityWaitReason;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 已提交执行活动记录
 * <p>
 * 固定原生调用与消息、计划节点归属，仅保存安全分类，不复制提示词、工具参数或工具结果。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionActivityDO {
    /** 活动业务ID */
    private String activityId;
    /** 归属用户ID */
    private String userId;
    /** 执行作用域ID */
    private String scopeId;
    /** 所属会话ID */
    private String conversationId;
    /** 来源用户消息ID */
    private String sourceMessageId;
    /** 普通回复ID */
    private String executionId;
    /** 目标任务ID */
    private String taskId;
    /** 工作节点ID */
    private String jobId;
    /** 原生执行绑定ID */
    private String bindingId;
    /** 原生运行ID */
    private String runId;
    /** 原生回合ID */
    private String turnId;
    /** 同用户幂等来源键 */
    private String sourceKey;
    /** 所属计划版本 */
    private long planVersion;
    /** 活动类型 */
    private ActivityType type;
    /** 活动分组 */
    private ActivityGroup group;
    /** 活动状态 */
    private ActivityStatus status;
    /** 等待原因 */
    private ActivityWaitReason waitReason;
    /** 服务端固定安全文案 */
    private String summary;
    /** 实际派发时间，缺失不补造 */
    private LocalDateTime startedAt;
    /** 实际结束时间 */
    private LocalDateTime endedAt;
    /** 公开活动版本 */
    private long version;
    /** 最后已应用原生序号 */
    private long nativeSequence;
}
