package com.agentsquad.squad.bo;

import com.agentsquad.squad.enums.WorkerJobStatus;

import java.util.List;

/**
 * 依赖规则所需的节点事实
 * <p>
 * 只包含当前计划内身份、状态及硬依赖，不依赖数据库、原生会话或工具结果正文。
 */
public record WorkerDependencyNodeBO(String nodeKey, WorkerJobStatus status, List<String> dependsOn) {
}
