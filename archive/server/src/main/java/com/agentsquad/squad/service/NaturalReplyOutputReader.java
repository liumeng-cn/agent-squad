package com.agentsquad.squad.service;

import com.agentsquad.squad.agent.session.NativeSessionGateway;
import com.agentsquad.squad.model.bo.NativeSubmissionReceiptBO;

import com.agentsquad.squad.model.bo.NaturalReplyEvidenceBO;
import com.agentsquad.squad.enums.ResponseExecutionStatus;
import com.agentsquad.squad.converter.ModelCompletionNormalizer;
import com.agentsquad.squad.enums.ModelEvidenceIssue;
import com.agentsquad.squad.enums.ModelFinishReason;
import com.agentsquad.squad.model.bo.ProjectionTargetBO;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.util.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 普通回复的自然输出日志适配
 * <p>
 * 只读取当前Run已提交前缀，核对模型停止原因和最终公开消息；未知、截断与正常完成明确分开，不执行模型。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class NaturalReplyOutputReader {
    private static final int READ_BATCH = 100;
    private static final int MAX_EVIDENCE_EVENTS = 10000;

    @Resource
    private NativeSessionGateway gateway;

    /**
     * 外部结果核对完成后重新读取同一Run的终结事实，不重新调用模型或依赖内存候选。
     */
    public NaturalReplyEvidenceBO readCompleted(ProjectionTargetBO target, NativeSubmissionReceiptBO receipt) {
        long after = 0;
        for (int page = 0; page < MAX_EVIDENCE_EVENTS / READ_BATCH; page++) {
            var events = gateway.readCommittedEvents(target, after, READ_BATCH);
            if (events.isEmpty()) {
                return null;
            }
            for (var event : events) {
                after = event.seq();
                if ("run/end".equals(event.type()) && "completed".equals(event.data().get("status"))
                        && Objects.equals(receipt.runId(), event.executionRunId())
                        && Objects.equals(receipt.turnId(), event.turnId())) {
                    return read(target, event);
                }
            }
        }
        return null;
    }

    public NaturalReplyEvidenceBO read(ProjectionTargetBO target, SessionEvent ended) {
        List<SessionEvent> runEvents = new ArrayList<>();
        long after = 0;
        int inspected = 0;
        while (after < ended.seq()) {
            var batch = gateway.readCommittedEvents(target, after, READ_BATCH);
            if (batch.isEmpty()) {
                return failed();
            }
            long previous = after;
            for (var event : batch) {
                if (event.seq() > ended.seq()) {
                    break;
                }
                if (++inspected > MAX_EVIDENCE_EVENTS) {
                    return failed();
                }
                after = event.seq();
                if (Objects.equals(ended.executionRunId(), event.executionRunId())
                        && Objects.equals(ended.turnId(), event.turnId())) {
                    runEvents.add(event);
                }
            }
            if (after == previous) {
                return failed();
            }
        }
        var output = runEvents.stream().filter(event -> event.type().equals("turn/output")).reduce((first, second) -> second).orElse(null);
        var modelEnd = runEvents.stream().filter(event -> event.type().equals("model/end")).reduce((first, second) -> second).orElse(null);
        if (output == null || modelEnd == null || !"completed".equals(output.data().get("status"))) {
            return failed();
        }
        Msg message = JsonUtils.getJsonCodec().convertValue(output.data().get("message"), Msg.class);
        if (message == null || message.getRole() != MsgRole.ASSISTANT
                || message.getGenerateReason() == GenerateReason.TOOL_RETURN_DIRECT
                || message.getTextContent() == null || message.getTextContent().isBlank()) {
            return failed();
        }
        Object call = modelEnd.data().get("modelCallId");
        if (!(call instanceof String modelCallId)) {
            return failed();
        }
        var completion = new ModelCompletionNormalizer().normalize(ended.executionRunId(), ended.turnId(), modelCallId, runEvents);
        if (completion.hasConfirmedNormalStop()) {
            return new NaturalReplyEvidenceBO(ResponseExecutionStatus.SUCCEEDED, message.getTextContent(), null);
        }
        if (completion.finishReason() == ModelFinishReason.LENGTH) {
            return new NaturalReplyEvidenceBO(ResponseExecutionStatus.PARTIAL, message.getTextContent(), "回答达到长度限制，已保留生成的内容。");
        }
        if (completion.finishReason() == ModelFinishReason.CONTENT_FILTER) {
            return new NaturalReplyEvidenceBO(ResponseExecutionStatus.PARTIAL, message.getTextContent(), "回答生成受限，内容可能不完整。");
        }
        if (completion.completionIssue() == ModelEvidenceIssue.MISSING_REASON
                || completion.completionIssue() == ModelEvidenceIssue.UNKNOWN_REASON) {
            return new NaturalReplyEvidenceBO(ResponseExecutionStatus.PARTIAL, message.getTextContent(), "未能确认回答是否完整，已保留可用内容。");
        }
        return failed();
    }

    private NaturalReplyEvidenceBO failed() {
        return new NaturalReplyEvidenceBO(ResponseExecutionStatus.FAILED, null, "本次回答未能可靠完成，请稍后重试。");
    }
}
