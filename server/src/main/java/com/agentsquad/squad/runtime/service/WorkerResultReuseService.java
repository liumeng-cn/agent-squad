package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.runtime.domain.ContextSnapshotRow;
import com.agentsquad.squad.runtime.domain.WorkerJobRow;
import com.agentsquad.squad.runtime.dto.PlanNode;
import com.agentsquad.squad.runtime.repository.ContextSnapshotMapper;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 重规划时的保守 Worker 结果复用判定。
 * <p>
 * D3 仅有同一 Task 的不可变用户约束快照，且 Worker 尚无 Skill 和外部工具权限；
 * 存在外部证据、依赖输入或任何授权差异时重新执行，绝不把旧结果默认视为当前事实。
 */
@Service
public class WorkerResultReuseService {

    @Resource
    private ContextSnapshotMapper snapshotMapper;

    @Resource
    private WorkerDefinitionService definitionService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 只从紧邻前一版计划查找同节点的终态结果；依赖节点必须已从同一旧版复用。
     * 调用方持有 Task 锁，旧结果不会并发变化。
     */
    public WorkerJobRow reusableNode(String userId, String taskId, PlanNode node,
                                     List<WorkerJobRow> previousJobs,
                                     Map<String, WorkerJobRow> reusedDependencies) {
        List<String> dependencies = node.dependsOn() == null ? List.of() : node.dependsOn();
        if (!reusedDependencies.keySet().containsAll(dependencies)) {
            return null;
        }
        WorkerDefinitionResponse definition = definitionService.requireEnabled(
                node.workerKey(), node.workerVersion());
        if (!definition.toolAllowlist().isEmpty()) {
            return null;
        }
        ContextSnapshotRow current = snapshotMapper.findOwned(userId, taskId,
                node.contextSnapshotId());
        if (current == null || !immutableUserEvidence(current, taskId)) {
            return null;
        }
        for (WorkerJobRow old : previousJobs) {
            if (!node.nodeKey().equals(old.nodeKey()) || !node.workerKey().equals(old.workerKey())
                    || !node.workerVersion().equals(old.workerVersion())
                    || !"SUCCEEDED".equals(old.status()) || old.resultJson() == null
                    || !sameDependencies(old, dependencies, previousJobs, reusedDependencies)) {
                continue;
            }
            JsonNode oldInput = read(old.inputJson());
            JsonNode oldResult = read(old.resultJson());
            if (oldInput == null || oldResult == null
                    || !node.goal().equals(oldInput.path("goal").asText())
                    || hasExternalEvidence(oldResult)) {
                continue;
            }
            ContextSnapshotRow previous = snapshotMapper.findOwned(userId, taskId,
                    oldInput.path("contextSnapshotId").asText());
            if (previous == null || !immutableUserEvidence(previous, taskId)
                    || previous.conversationGeneration() != current.conversationGeneration()
                    || previous.memoryRevision() != current.memoryRevision()
                    || !previous.contentHash().equals(current.contentHash())) {
                continue;
            }
            return old;
        }
        return null;
    }

    private boolean sameDependencies(WorkerJobRow old, List<String> dependencies,
                                     List<WorkerJobRow> previousJobs,
                                     Map<String, WorkerJobRow> reusedDependencies) {
        JsonNode names = read(old.dependsOnJson());
        if (dependencies.isEmpty()) {
            return names == null || names.isEmpty();
        }
        if (names == null || !names.isArray() || names.size() != dependencies.size()) {
            return false;
        }
        Set<String> oldKeys = names.valueStream().map(JsonNode::asText).collect(Collectors.toSet());
        if (!oldKeys.equals(Set.copyOf(dependencies))) {
            return false;
        }
        for (String key : dependencies) {
            WorkerJobRow oldDependency = previousJobs.stream()
                    .filter(job -> key.equals(job.nodeKey()))
                    .findFirst().orElse(null);
            WorkerJobRow reused = reusedDependencies.get(key);
            if (oldDependency == null || reused == null
                    || !oldDependency.jobId().equals(reused.jobId())) {
                return false;
            }
        }
        return true;
    }

    private boolean immutableUserEvidence(ContextSnapshotRow snapshot, String taskId) {
        JsonNode items = read(snapshot.itemsJson());
        return snapshot.memoryRevision() == 0 && items != null && items.isArray()
                && items.size() == 1
                && "USER_CONSTRAINT".equals(items.get(0).path("kind").asText())
                && "USER".equals(items.get(0).path("trust").asText())
                && taskId.equals(items.get(0).path("sourceId").asText())
                && "1".equals(items.get(0).path("sourceVersion").asText())
                && snapshot.contentHash().equals(CanonicalJson.sha256(objectMapper, snapshot.itemsJson()));
    }

    private boolean hasExternalEvidence(JsonNode result) {
        JsonNode refs = result.path("evidenceRefs");
        return refs.isArray() && !refs.isEmpty();
    }

    private JsonNode read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return RuntimePayloads.readJson(objectMapper, value);
        } catch (Exception exception) {
            return null;
        }
    }
}
