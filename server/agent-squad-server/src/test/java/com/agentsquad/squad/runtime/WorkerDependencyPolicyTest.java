package com.agentsquad.squad.runtime;

import com.agentsquad.squad.common.enums.worker.WorkerJobStatus;
import com.agentsquad.squad.domain.entity.WorkerDependencyNode;
import com.agentsquad.squad.domain.entity.WorkerDependencyPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 硬依赖失败规则验证
 * <p>
 * 验证链式传播、恢复等待与并行分支隔离，不需要启动数据库或模型。
 */
class WorkerDependencyPolicyTest {
    @Test
    void skipsFailedChainRegardlessOfNodeOrder() {
        assertThat(WorkerDependencyPolicy.failedDependents(List.of(
                node("leaf", WorkerJobStatus.BLOCKED, "middle"),
                node("middle", WorkerJobStatus.BLOCKED, "root"),
                node("root", WorkerJobStatus.FAILED), node("parallel", WorkerJobStatus.RUNNING))))
                .containsExactlyInAnyOrder("middle", "leaf");
    }

    @Test
    void doesNotSkipRecoverableOrWaitingWork() {
        for (var state : List.of(WorkerJobStatus.RECOVERY_BLOCKED, WorkerJobStatus.RECOVERING,
                WorkerJobStatus.WAITING_INPUT, WorkerJobStatus.WAITING_APPROVAL, WorkerJobStatus.SUPERSEDED)) {
            assertThat(WorkerDependencyPolicy.failedDependents(List.of(
                    node("root", state), node("next", WorkerJobStatus.BLOCKED, "root")))).isEmpty();
        }
        for (var state : List.of(WorkerJobStatus.PARTIAL, WorkerJobStatus.CANCELLED, WorkerJobStatus.SKIPPED)) {
            assertThat(WorkerDependencyPolicy.failedDependents(List.of(
                    node("root", state), node("next", WorkerJobStatus.BLOCKED, "root")))).containsExactly("next");
        }
    }

    @Test
    void rejectsMissingAndDuplicateDependencies() {
        assertThatThrownBy(() -> WorkerDependencyPolicy.failedDependents(List.of(node("next", WorkerJobStatus.BLOCKED, "missing"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkerDependencyPolicy.failedDependents(List.of(node("same", WorkerJobStatus.READY), node("same", WorkerJobStatus.READY))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private WorkerDependencyNode node(String key, WorkerJobStatus status, String... dependencies) {
        return new WorkerDependencyNode(key, status, List.of(dependencies));
    }
}
