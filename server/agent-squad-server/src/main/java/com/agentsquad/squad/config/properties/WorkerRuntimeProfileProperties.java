package com.agentsquad.squad.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Worker 运行限制配置
 * <p>
 * 控制单个 Worker 的模型、工具与恢复上限；不能放宽父目标的共享预算或并行上限。
 */
@Getter
@Setter
public class WorkerRuntimeProfileProperties {

    /** 一次 Worker 执行允许的模型调用次数 */
    private int maxModelCalls;

    /** 一次 Worker 执行允许的工具调用次数 */
    private int maxToolCalls;

    /** 一次 Worker 执行允许的恢复尝试次数 */
    private int maxRecoveryAttempts;

    /** 单次模型请求超时，单位秒 */
    private int modelRequestTimeoutSeconds;

    /** 单次工具调用超时，单位秒 */
    private int toolTimeoutSeconds;
}
