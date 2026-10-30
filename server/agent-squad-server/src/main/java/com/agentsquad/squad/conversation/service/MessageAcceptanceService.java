package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.domain.MessageSubmissionRow;
import com.agentsquad.squad.conversation.domain.ResponseExecutionStatus;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.dto.MessageReceipt;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户消息的可靠受理入口。
 * <p>
 * 校验与规范化请求，调用独立原子事务；首发并发唯一键冲突只补查原收据，不重建对话或启动重复执行。
 */
@Service
@Slf4j
public class MessageAcceptanceService {

    @Resource
    private MessageAcceptanceWriter writer;

    @Resource
    private MessageAcceptanceMapper mapper;

    @Resource
    private ExecutionScopeService scopeService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 先持久消息与回复命令；同键同规范化输入只返回原身份，在途目标不阻断新消息。
     */
    public MessageReceipt accept(String userId, String conversationId, String key, MessageCreateRequest request) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw invalid("消息幂等键长度必须为1到160");
        }
        MessageCreateRequest normalized = normalize(request);
        String scopeKey = conversationId == null ? "message.create:first" : "message.create:" + conversationId;
        String hash = CanonicalJson.sha256(objectMapper, RuntimePayloads.toJson(objectMapper,
                Map.of("text", normalized.text(), "artifactIds", normalized.artifactIds())));
        MessageSubmissionRow row;
        try {
            row = writer.accept(userId, conversationId, scopeKey, key, normalized, hash);
        } catch (DuplicateKeyException duplicate) {
            // 独立写事务已回滚；唯一键胜出者已提交，才能从新读视图补回执。
            row = mapper.findSubmission(userId, scopeKey, key);
            if (row == null) {
                throw duplicate;
            }
        }
        if (row == null) {
            throw new IllegalStateException("消息事务未形成完整受理收据");
        }
        if (!hash.equals(row.payloadHash())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的消息或附件不一致");
        }
        scopeService.requireReadable(userId, row.scopeId());
        log.info("消息受理回执 conversationId={} messageId={} executionId={} commandId={}",
                row.conversationId(), row.messageId(), row.executionId(), row.commandId());
        return new MessageReceipt(row.conversationId(), row.messageId(), row.executionId(), row.commandId(),
                ResponseExecutionStatus.valueOf(row.status()),
                row.conversationId() + ":" + row.generation() + ":" + row.receiptSequence());
    }

    private MessageCreateRequest normalize(MessageCreateRequest request) {
        if (request == null || request.text() == null || request.text().isBlank() || request.text().length() > 20000) {
            throw invalid("消息内容长度必须为1到20000");
        }
        List<String> supplied = request.artifactIds() == null ? List.of() : request.artifactIds();
        if (supplied.size() > 20) {
            throw invalid("附件最多20个");
        }
        List<String> ids = new ArrayList<>();
        for (String id : supplied) {
            if (id == null || !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                throw invalid("附件引用必须是有效的产物ID");
            }
            ids.add(UUID.fromString(id).toString());
        }
        if (ids.stream().distinct().count() != ids.size()) {
            throw invalid("附件不能重复");
        }
        return new MessageCreateRequest(request.text(), List.copyOf(ids));
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
