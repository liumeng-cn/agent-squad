package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.bridge.NativeEventView;
import com.agentsquad.squad.runtime.domain.FinalizationRow;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.runtime.repository.CompletionMapper;
import com.agentsquad.squad.runtime.repository.ConversationMapper;
import com.agentsquad.squad.runtime.repository.ProjectionMapper;
import com.agentsquad.squad.runtime.repository.TaskEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目标终态裁决。仅接受同一 Master Binding/Turn 的持久完成候选与成功的原生回合事实。
 *
 * <p>由原生事件投影事务调用；终态、最终答案事件、会话占用释放一起提交或一起回滚。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class TaskFinalizerService {

    private static final Set<String> ALLOWED_OUTCOMES = Set.of("SUCCEEDED", "PARTIAL");

    @Resource
    private CompletionMapper completionMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private ProjectionMapper projectionMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 没有候选、来源不符或取消中时保持任务原状；写入失败则抛异常回滚投影水位，等待重试。
     */
    public boolean tryComplete(ProjectionTarget target, NativeEventView event) {
        FinalizationRow row = completionMapper.lockTask(target.userId(), target.taskId());
        if (row == null || !"RUNNING".equals(row.status()) || row.cancellationRequested()
                || row.proposalJson() == null || !target.bindingId().equals(row.proposalBindingId())
                || event.turnId() == null || !event.turnId().equals(row.proposalTurnId())) {
            return false;
        }
        JsonNode proposal = parseProposal(row.proposalJson());
        String outcome = proposal.path("outcome").asText();
        String answer = proposal.path("finalAnswer").asText();
        if (!ALLOWED_OUTCOMES.contains(outcome) || answer.isBlank()) {
            throw new IllegalStateException("持久完成候选不满足终态契约");
        }
        if (completionMapper.completeTask(row.userId(), row.taskId(), target.bindingId(),
                event.turnId(), outcome) != 1) {
            throw new IllegalStateException("目标终态写入竞争");
        }
        if (conversationMapper.clearActiveTask(row.userId(), row.conversationId(), row.taskId()) != 1) {
            throw new IllegalStateException("对话目标占用与终态不一致");
        }
        publish(row, event, "answer.completed", Map.of("text", answer, "evidenceRefs", List.of()));
        publish(row, event, "task.completed", Map.of("status", outcome, "version", row.version() + 1));
        log.info("目标已终结 taskId={} bindingId={} turnId={} outcome={}",
                row.taskId(), target.bindingId(), event.turnId(), outcome);
        return true;
    }

    private void publish(FinalizationRow row, NativeEventView event, String type, Object payload) {
        if (projectionMapper.reserveEventSequence(row.userId(), row.taskId()) != 1) {
            throw new IllegalStateException("最终事件序号保留失败");
        }
        Long sequence = projectionMapper.eventSequence(row.userId(), row.taskId());
        if (sequence == null) {
            throw new IllegalStateException("最终事件序号读取失败");
        }
        taskEventMapper.insertProjected(row.userId(), row.taskId(), sequence,
                "completion:" + row.taskId() + ":" + event.turnId() + ":" + type, type,
                toJson(payload), LocalDateTime.now(ZoneOffset.UTC));
    }

    private JsonNode parseProposal(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("持久完成候选无法解析", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("最终事件无法序列化", exception);
        }
    }
}
