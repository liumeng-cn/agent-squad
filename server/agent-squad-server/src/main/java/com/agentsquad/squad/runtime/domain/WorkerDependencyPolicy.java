package com.agentsquad.squad.runtime.domain;

import com.agentsquad.squad.common.enums.worker.WorkerJobStatus;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 硬依赖失败传播规则
 * <p>
 * 只跳过尚未执行且依赖已明确失败的工作，递归处理下游；恢复受限、正常等待及旧计划替换不伪装为失败。
 */
public final class WorkerDependencyPolicy {
    private WorkerDependencyPolicy() {
    }

    public static Set<String> failedDependents(List<WorkerDependencyNode> nodes) {
        var states = new HashMap<String, WorkerJobStatus>();
        for (var node : nodes) {
            if (node.nodeKey() == null || node.status() == null || states.put(node.nodeKey(), node.status()) != null) {
                throw new IllegalArgumentException("依赖节点身份或状态无效");
            }
        }
        var skipped = new LinkedHashSet<String>();
        boolean changed;
        do {
            changed = false;
            for (var node : nodes) {
                if (states.get(node.nodeKey()) != WorkerJobStatus.BLOCKED) {
                    continue;
                }
                for (String dependency : node.dependsOn()) {
                    var state = states.get(dependency);
                    if (state == null) {
                        throw new IllegalArgumentException("依赖节点不属于当前计划");
                    }
                    if (state.preventsDependentExecution()) {
                        states.put(node.nodeKey(), WorkerJobStatus.SKIPPED);
                        skipped.add(node.nodeKey());
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        return skipped;
    }
}
