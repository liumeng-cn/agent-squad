package com.agentsquad.squad.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Worker 运行限制配置
 *
 * 控制单个 Worker 的模型、工具与恢复上限；不能放宽父目标的共享预算或并行上限。
 */
@Getter
@Setter
public class WorkerRuntimeProfile {

    private int maxModelCalls;

    private int maxToolCalls;

    private int maxRecoveryAttempts;

    private int modelRequestTimeoutSeconds;

    private int toolTimeoutSeconds;
}
