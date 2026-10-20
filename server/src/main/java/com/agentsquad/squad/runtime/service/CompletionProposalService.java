package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.domain.CompletionContextRow;
import com.agentsquad.squad.runtime.dto.CompletionProposalReceipt;
import com.agentsquad.squad.runtime.repository.CompletionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Master 完成候选服务。只保存候选，不修改任务终态或释放对话占用。
 *
 * <p>用户、原生会话和 Turn 来自框架运行上下文，任务归属由数据库绑定解析；模型不能指定目标 ID。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CompletionProposalService {

    private static final Set<String> ALLOWED_OUTCOMES = Set.of("SUCCEEDED", "PARTIAL");

    @Resource
    private CompletionMapper completionMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 同一原生 Turn 的相同候选返回原回执；不同内容或不同 Turn 的重复提交拒绝。
     * 事务提交后即使工具回包丢失，重放也不会生成第二个候选。
     */
    @Transactional
    public CompletionProposalReceipt propose(String userId, String sessionId, String turnId,
                                             String toolCallId, String outcome, String finalAnswer) {
        if (isBlank(userId) || isBlank(sessionId) || isBlank(turnId) || isBlank(toolCallId)
                || turnId.length() > 64 || toolCallId.length() > 128) {
            throw new IllegalStateException("完成候选缺少可信运行身份");
        }
        if (!ALLOWED_OUTCOMES.contains(outcome) || isBlank(finalAnswer)
                || finalAnswer.length() > 200000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "完成候选内容无效");
        }

        CompletionContextRow context = completionMapper.lockByMasterSession(userId, sessionId);
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Master 会话未绑定目标");
        }
        if (context.cancellationRequested()
                || !("QUEUED".equals(context.taskStatus()) || "RUNNING".equals(context.taskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已不能提交完成候选");
        }

        Map<String, String> proposal = new LinkedHashMap<>();
        proposal.put("outcome", outcome);
        proposal.put("finalAnswer", finalAnswer);
        String proposalJson = toJson(proposal);
        String proposalHash = sha256(proposalJson);
        if (context.proposalJson() != null) {
            if (turnId.equals(context.proposalTurnId()) && proposalHash.equals(context.proposalHash())) {
                return new CompletionProposalReceipt(context.taskId(), context.bindingId(), turnId,
                        context.proposalToolCallId(), "RECORDED");
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已有不同的完成候选");
        }
        if (completionMapper.saveProposal(userId, context.taskId(), context.bindingId(), turnId,
                toolCallId, proposalHash, proposalJson) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "完成候选提交时目标状态已变化");
        }
        log.info("Master 完成候选已持久化 taskId={} bindingId={} turnId={} outcome={} answerLength={}",
                context.taskId(), context.bindingId(), turnId, outcome, finalAnswer.length());
        return new CompletionProposalReceipt(context.taskId(), context.bindingId(), turnId,
                toolCallId, "RECORDED");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("完成候选无法序列化", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
