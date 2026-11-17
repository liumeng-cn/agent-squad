package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.SessionExecutionStatus;
import lombok.Data;

import java.time.Instant;

/**
 * 会话执行状态
 * <p>
 * v2会话列表的固定结构模型，字段不包含原始提示词或工具结果
 */
@Data
public class SessionExecutionVO {
    /**
     * 观察到的运行状态
     */
    private SessionExecutionStatus status;
    /**
     * 观察时间，可空
     */
    private Instant observedAt;
    /**
     * 是否需要重新核对
     */
    private boolean stale;
}
