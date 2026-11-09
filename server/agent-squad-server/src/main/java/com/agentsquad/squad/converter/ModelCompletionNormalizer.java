package com.agentsquad.squad.converter;

import com.agentsquad.squad.bo.ModelTokenUsageBO;
import com.agentsquad.squad.bo.NormalizedModelCompletionBO;

import io.agentscope.core.session.SessionEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.agentsquad.squad.enums.ModelCompletionStatus;
import com.agentsquad.squad.enums.ModelEvidenceIssue;
import com.agentsquad.squad.enums.ModelFinishReason;

/**
 * 已提交模型事件规范化
 * <p>
 * 适配模块按固定 Run、Turn 和模型调用读取完整日志区间，核对停止原因与独立用量来源。
 * 无数据库、日志或发布副作用；重复事件可重放，缺号和冲突必须补齐后重新计算。
 */
public final class ModelCompletionNormalizer {

    /**
     * 使用同一绑定的完整连续已提交区间计算事实，区间须覆盖模型 dispatch 到 end
     * <p>
     * 允许乱序交付和完全相同的重投；不接受跨尝试证据拼接，也不从 Publisher 完成推断成功。
     */
    public NormalizedModelCompletionBO normalize(String runId, String turnId, String modelCallId,
                                               List<SessionEvent> committedEvents) {
        requireIdentity(runId, turnId, modelCallId);
        TreeMap<Long, SessionEvent> ordered = new TreeMap<>();
        Map<String, SessionEvent> ids = new HashMap<>();
        for (SessionEvent event : List.copyOf(committedEvents)) {
            SessionEvent sameSequence = ordered.putIfAbsent(event.seq(), event);
            SessionEvent sameId = ids.putIfAbsent(event.eventId(), event);
            if (sameSequence != null && !sameSequence.equals(event) || sameId != null && !sameId.equals(event)) {
                return incomplete(runId, turnId, modelCallId, ModelEvidenceIssue.EVENT_CONFLICT);
            }
        }
        Long previous = null;
        for (long sequence : ordered.keySet()) {
            if (previous != null && sequence != previous + 1) {
                return incomplete(runId, turnId, modelCallId, ModelEvidenceIssue.EVENT_GAP);
            }
            previous = sequence;
        }

        Set<String> reasons = new HashSet<>();
        Long reasonSequence = null;
        Long endSequence = null;
        ModelCompletionStatus status = ModelCompletionStatus.UNKNOWN;
        ModelEvidenceIssue lifecycleIssue = ModelEvidenceIssue.NONE;
        ModelEvidenceIssue usageIssue = ModelEvidenceIssue.NONE;
        ModelTokenUsageBO lastChunkUsage = null;
        Long chunkUsageSequence = null;
        ModelTokenUsageBO endUsage = null;
        boolean dispatched = false;

        for (SessionEvent event : ordered.values()) {
            if (!runId.equals(event.executionRunId()) || !turnId.equals(event.turnId())) {
                continue;
            }
            if (!event.type().equals("model/dispatch") && !event.type().equals("model/chunk")
                    && !event.type().equals("model/end")) {
                continue;
            }
            Map<String, Object> data;
            try {
                data = event.data();
            } catch (RuntimeException exception) {
                return incomplete(runId, turnId, modelCallId, ModelEvidenceIssue.INVALID_PAYLOAD);
            }
            if (data == null) {
                return incomplete(runId, turnId, modelCallId, ModelEvidenceIssue.INVALID_PAYLOAD);
            }
            if (!modelCallId.equals(data.get("modelCallId"))) {
                continue;
            }
            if (event.type().equals("model/dispatch")) {
                if (dispatched || endSequence != null) {
                    lifecycleIssue = ModelEvidenceIssue.INVALID_LIFECYCLE;
                }
                dispatched = true;
                continue;
            }
            if (!event.type().equals("model/chunk") && !event.type().equals("model/end")) {
                continue;
            }
            if (!dispatched || endSequence != null) {
                lifecycleIssue = ModelEvidenceIssue.INVALID_LIFECYCLE;
            }
            boolean ending = event.type().equals("model/end");
            Map<?, ?> payload;
            if (ending) {
                payload = data;
                endSequence = event.seq();
                status = ModelCompletionStatus.fromNative(data.get("status"));
            } else if (data.get("chunk") instanceof Map<?, ?> chunk) {
                payload = chunk;
            } else {
                lifecycleIssue = ModelEvidenceIssue.INVALID_PAYLOAD;
                continue;
            }

            Object reason = payload.get("finishReason");
            if (reason instanceof String value && !value.isBlank()) {
                reasons.add(value);
                if (reasonSequence == null) {
                    reasonSequence = event.seq();
                }
            } else if (reason != null && !(reason instanceof String)) {
                lifecycleIssue = ModelEvidenceIssue.INVALID_PAYLOAD;
            }
            if (payload.get("usage") != null) {
                try {
                    ModelTokenUsageBO usage = readUsage(payload.get("usage"));
                    if (ending) {
                        endUsage = usage;
                    } else {
                        lastChunkUsage = usage;
                        chunkUsageSequence = event.seq();
                    }
                } catch (IllegalArgumentException exception) {
                    usageIssue = ModelEvidenceIssue.INVALID_USAGE;
                }
            }
        }

        ModelFinishReason reason = reasons.size() == 1
                ? ModelFinishReason.fromNative(reasons.iterator().next()) : ModelFinishReason.UNKNOWN;
        ModelEvidenceIssue issue = completionIssue(lifecycleIssue, dispatched, endSequence, status, reasons, reason);
        if (usageIssue == ModelEvidenceIssue.NONE) {
            if (lifecycleIssue != ModelEvidenceIssue.NONE || !dispatched || endSequence == null) {
                usageIssue = issue;
            } else if (endUsage != null && lastChunkUsage != null && !endUsage.equals(lastChunkUsage)) {
                usageIssue = ModelEvidenceIssue.CONFLICTING_USAGE;
            } else if (endUsage == null && lastChunkUsage == null) {
                usageIssue = ModelEvidenceIssue.MISSING_USAGE;
            }
        }
        ModelTokenUsageBO usage = usageIssue == ModelEvidenceIssue.NONE
                ? (endUsage != null ? endUsage : lastChunkUsage) : null;
        Long usageSequence = usage == null ? null : (endUsage != null ? endSequence : chunkUsageSequence);
        return new NormalizedModelCompletionBO(runId, turnId, modelCallId, status, reason, issue,
                reasonSequence, endSequence, usage, usageIssue, usageSequence);
    }

    private ModelEvidenceIssue completionIssue(ModelEvidenceIssue lifecycleIssue, boolean dispatched,
                                               Long endSequence, ModelCompletionStatus status, Set<String> reasons,
                                               ModelFinishReason reason) {
        if (lifecycleIssue != ModelEvidenceIssue.NONE) {
            return lifecycleIssue;
        }
        if (!dispatched) {
            return ModelEvidenceIssue.MISSING_DISPATCH;
        }
        if (endSequence == null) {
            return ModelEvidenceIssue.MISSING_END;
        }
        if (status == ModelCompletionStatus.UNKNOWN) {
            return ModelEvidenceIssue.UNKNOWN_STATUS;
        }
        if (reasons.isEmpty()) {
            return ModelEvidenceIssue.MISSING_REASON;
        }
        if (reasons.size() > 1) {
            return ModelEvidenceIssue.CONFLICTING_REASON;
        }
        return reason == ModelFinishReason.UNKNOWN ? ModelEvidenceIssue.UNKNOWN_REASON : ModelEvidenceIssue.NONE;
    }

    private ModelTokenUsageBO readUsage(Object value) {
        if (!(value instanceof Map<?, ?> usage) || !usage.containsKey("inputTokens") || !usage.containsKey("outputTokens")) {
            throw new IllegalArgumentException("用量缺少必要计数");
        }
        return new ModelTokenUsageBO(counter(usage, "inputTokens"), counter(usage, "outputTokens"),
                counter(usage, "cachedTokens"), counter(usage, "cacheCreationTokens"),
                counter(usage, "reasoningTokens"), counter(usage, "toolUsePromptTokens"));
    }

    private long counter(Map<?, ?> usage, String name) {
        Object value = usage.get(name);
        if (value == null && !name.equals("inputTokens") && !name.equals("outputTokens")) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("用量计数结构无效");
        }
        try {
            long count = new BigDecimal(number.toString()).longValueExact();
            if (count < 0 || count > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("用量计数超出SDK范围");
            }
            return count;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("用量计数不是有效整数", exception);
        }
    }

    private NormalizedModelCompletionBO incomplete(String runId, String turnId, String modelCallId, ModelEvidenceIssue issue) {
        return new NormalizedModelCompletionBO(runId, turnId, modelCallId, ModelCompletionStatus.UNKNOWN,
                ModelFinishReason.UNKNOWN, issue, null, null, null, issue, null);
    }

    private void requireIdentity(String runId, String turnId, String modelCallId) {
        if (runId == null || runId.isBlank() || turnId == null || turnId.isBlank() || modelCallId == null || modelCallId.isBlank()) {
            throw new IllegalArgumentException("模型结束事实缺少固定运行身份");
        }
    }
}
