package com.agentsquad.squad.service;

import com.agentsquad.squad.utils.CanonicalJson;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.po.ContextSnapshotPO;
import com.agentsquad.squad.model.po.WorkerJobPO;
import com.agentsquad.squad.model.bo.PlanNodeBO;
import com.agentsquad.squad.mapper.ContextSnapshotMapper;
import com.agentsquad.squad.model.vo.WorkerDefinitionVO;
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
 * 仅复用同一目标、相同局部材料与固定资料代际的结果；
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
    public WorkerJobPO reusableNode(Long userId, Long taskId, PlanNodeBO node,
                                    List<WorkerJobPO> previousJobs,
                                    Map<String, WorkerJobPO> reusedDependencies) {
        List<String> dependencies = node.dependsOn() == null ? List.of() : node.dependsOn();
        if (!reusedDependencies.keySet().containsAll(dependencies)) {
            return null;
        }
        WorkerDefinitionVO definition = definitionService.requireEnabled(
                node.workerKey(), node.workerVersion());
        if (!definition.toolAllowlist().isEmpty()) {
            return null;
        }
        ContextSnapshotPO current = snapshotMapper.findOwned(userId, taskId,
                node.contextSnapshotId());
        if (current == null || !immutableUserEvidence(current, taskId)) {
            return null;
        }
        for (WorkerJobPO old : previousJobs) {
            if (!node.nodeKey().equals(old.getNodeKey()) || !node.workerKey().equals(old.getWorkerKey())
                    || !node.workerVersion().equals(old.getWorkerVersion())
                    || !"SUCCEEDED".equals(old.getStatus()) || old.getResultJson() == null
                    || !sameDependencies(old, dependencies, previousJobs, reusedDependencies)) {
                continue;
            }
            JsonNode oldInput = read(old.getInputJson());
            JsonNode oldResult = read(old.getResultJson());
            if (oldInput == null || oldResult == null
                    || !node.goal().equals(oldInput.path("goal").asText())
                    || hasExternalEvidence(oldResult)) {
                continue;
            }
            ContextSnapshotPO previous = snapshotMapper.findOwned(userId, taskId,
                    Long.parseLong(oldInput.path("contextSnapshotId").asText()));
            if (previous == null || !immutableUserEvidence(previous, taskId)
                    || previous.getConversationGeneration() != current.getConversationGeneration()
                    || previous.getGoalRevision() != current.getGoalRevision()
                    || previous.getMemoryRevision() != current.getMemoryRevision()
                    || !previous.getContentHash().equals(current.getContentHash())) {
                continue;
            }
            return old;
        }
        return null;
    }

    private boolean sameDependencies(WorkerJobPO old, List<String> dependencies,
                                     List<WorkerJobPO> previousJobs,
                                     Map<String, WorkerJobPO> reusedDependencies) {
        JsonNode names = read(old.getDependsOnJson());
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
            WorkerJobPO oldDependency = previousJobs.stream()
                    .filter(job -> key.equals(job.getNodeKey()))
                    .findFirst().orElse(null);
            WorkerJobPO reused = reusedDependencies.get(key);
            if (oldDependency == null || reused == null
                    || !oldDependency.getJobId().equals(reused.getJobId())) {
                return false;
            }
        }
        return true;
    }

    private boolean immutableUserEvidence(ContextSnapshotPO snapshot, Long taskId) {
        JsonNode items = read(snapshot.getItemsJson());
        if (items == null || !items.isArray() || items.isEmpty()
                || !snapshot.getContentHash().equals(CanonicalJson.sha256(objectMapper, snapshot.getItemsJson()))) {
            return false;
        }
        for (JsonNode item : items) {
            String kind = item.path("kind").asText();
            if (!java.util.Set.of("USER_CONSTRAINT", "USER_ORIGIN_REFERENCE", "USER_GOAL_REPLACEMENT",
                    "USER_SUPPLEMENT", "SKILL_REFERENCE", "EXPLICIT_MEMORY").contains(kind)) {
                return false;
            }
        }
        return String.valueOf(taskId).equals(items.get(0).path("sourceId").asText());
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
