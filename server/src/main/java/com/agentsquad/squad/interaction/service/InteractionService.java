package com.agentsquad.squad.interaction.service;

import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.conversation.domain.CommandRow;
import com.agentsquad.squad.conversation.domain.GoalTaskStatus;
import com.agentsquad.squad.conversation.dto.CommandReceipt;
import com.agentsquad.squad.conversation.repository.CommandMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.interaction.domain.InteractionRow;
import com.agentsquad.squad.interaction.repository.InteractionMapper;
import com.agentsquad.squad.tools.mock.MockTicketConnector;
import com.agentsquad.squad.tools.operation.OperationExecutor;
import com.agentsquad.squad.tools.operation.OperationService;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.domain.OperationStatus;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 持久输入与审批答复服务（M07），随投递开关启用。
 *
 * <p>绑定：框架 interaction/requested 只绑定到 sq_interaction，不改写框架日志；
 * 有操作账本记录的挂起是 APPROVAL，request_input 挂起是 INPUT，其余原样跳过。
 * 答复：批准先在账本内派发成功再排队 RESPOND 命令，拒绝直接取消操作；
 * 同一交互重复答复同内容返回原回执，不同内容拒绝。答案文本回填原生 external_execution，
 * 审批决定以操作账本为唯一权威。只读查询与视图映射在 InteractionQueryService。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class InteractionService {

    private static final String KIND_INPUT = "INPUT";
    private static final String KIND_APPROVAL = "APPROVAL";
    private static final String TOOL_MOCK_TICKET = "mock_ticket_create";
    private static final long TICKET_RESERVED_UNITS = 1000000L;

    @Resource
    private InteractionMapper interactionMapper;

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private OperationExecutor operationExecutor;

    @Resource
    private OperationService operationService;

    @Resource
    private MockTicketConnector mockTicketConnector;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${agentsquad.interaction.ttl-hours:24}")
    private long ttlHours;

    /**
     * 绑定框架挂起请求；重复事件按唯一键去重，非受控工具挂起不产生交互。
     * frameworkTurnId 来自原生日志事件，用于在账本内定位对应原生工具调用。
     */
    @Transactional
    public BindingResult bindFrameworkRequest(String userId, String taskId, String bindingId,
                                              String jobId, String frameworkTurnId,
                                              String frameworkRequestId,
                                              Map<String, Object> call) {
        InteractionRow existing = interactionMapper.findByFrameworkRequest(userId, bindingId,
                frameworkRequestId);
        if (existing != null) {
            return new BindingResult(existing.interactionId(), existing.kind());
        }
        String toolName = String.valueOf(call.get("name"));
        OperationRow operation = frameworkTurnId == null ? null
                : operationMapper.findByNativeCall(userId, bindingId, frameworkTurnId,
                frameworkRequestId);
        String interactionId = UUID.randomUUID().toString();
        if (operation != null) {
            String question = buildApprovalQuestion(toolName, operation);
            interactionMapper.insert(interactionId, userId, taskId, jobId, bindingId,
                    operation.operationId(), "FRAMEWORK", frameworkRequestId, KIND_APPROVAL,
                    question, fixedApprovalSchema(), 0L, operation.requestHash(),
                    operation.connectionRevision(), LocalDateTime.now().plusHours(ttlHours),
                    userId, "model");
            log.info("审批交互已绑定 interactionId={} operationId={} toolKey={}",
                    interactionId, operation.operationId(), operation.toolKey());
            return new BindingResult(interactionId, KIND_APPROVAL);
        }
        if ("request_input".equals(toolName)) {
            String question = extractQuestion(call);
            String answerSchemaJson = extractAnswerSchema(call);
            interactionMapper.insert(interactionId, userId, taskId, jobId, bindingId,
                    null, "FRAMEWORK", frameworkRequestId, KIND_INPUT,
                    question, answerSchemaJson, 0L,
                    ContentDigests.sha256(question), 0L,
                    LocalDateTime.now().plusHours(ttlHours), userId, "model");
            log.info("追问交互已绑定 interactionId={} bindingId={}", interactionId, bindingId);
            return new BindingResult(interactionId, KIND_INPUT);
        }
        log.warn("未识别的挂起请求不绑定交互 bindingId={} requestId={} tool={}",
                bindingId, frameworkRequestId, toolName);
        return null;
    }

    /**
     * 框架侧解析事件落账：仅把 OPEN 推到 RESPONDED，幂等无害；返回最新交互行供投影发布。
     */
    @Transactional
    public InteractionRow resolveFromFramework(String userId, String bindingId, String requestId) {
        InteractionRow row = interactionMapper.findByFrameworkRequest(userId, bindingId, requestId);
        if (row == null) {
            return null;
        }
        interactionMapper.markResolvedByFramework(userId, bindingId, requestId,
                RuntimePayloads.toJson(objectMapper, Map.of("resolvedBy", "framework")));
        return interactionMapper.findByFrameworkRequest(userId, bindingId, requestId);
    }

    /**
     * 按开放交互判断目标应进入的等待态；审批优先于追问，无开放交互返回 null。
     */
    public String waitingStatusOf(String userId, String taskId) {
        if (interactionMapper.countOpenByKind(userId, taskId, KIND_APPROVAL) > 0) {
            return GoalTaskStatus.WAITING_APPROVAL.name();
        }
        if (interactionMapper.countOpenByKind(userId, taskId, KIND_INPUT) > 0) {
            return GoalTaskStatus.WAITING_INPUT.name();
        }
        return null;
    }

    /**
     * 用户答复：批准派发、拒绝取消、追问回填；全部先落账本再排队 RESPOND 命令。
     */
    @Transactional
    public CommandReceipt respond(AuthView actor, String interactionId, String idempotencyKey,
                                  long expectedVersion, String answerKind, Boolean approve,
                                  String confirmedRequestHash, Map<String, Object> inputValue) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                    "Idempotency-Key 必须是 1-160 个字符");
        }
        InteractionRow row = interactionMapper.findOwned(actor.userId(), interactionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "交互不存在");
        }
        String scopeKey = "interaction:" + interactionId;
        Map<String, Object> answerJson = new LinkedHashMap<>();
        answerJson.put("kind", answerKind);
        String answerText = null;
        if (KIND_APPROVAL.equals(row.kind())) {
            if (!KIND_APPROVAL.equals(answerKind) || approve == null) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "审批交互必须提交 APPROVAL 答复");
            }
            if (!row.requestHash().equals(confirmedRequestHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "确认摘要与审批内容不一致，请刷新页面后重试");
            }
            answerJson.put("approve", approve);
            answerJson.put("confirmedRequestHash", confirmedRequestHash);
        } else if (KIND_INPUT.equals(row.kind())) {
            if (!KIND_INPUT.equals(answerKind) || inputValue == null || inputValue.isEmpty()) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "追问交互必须提交 INPUT 内容");
            }
            String valueJson = RuntimePayloads.toJson(objectMapper, inputValue);
            if (valueJson.getBytes(StandardCharsets.UTF_8).length > 8192) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "答复内容超过 8KB");
            }
            answerJson.put("value", inputValue);
            answerText = valueJson;
        } else {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "未知交互类型");
        }
        String answerJsonText = RuntimePayloads.toJson(objectMapper, answerJson);

        if (!"OPEN".equals(row.status())) {
            if (sameJson(answerJsonText, row.responseJson())) {
                CommandRow prior = commandMapper.findByKey(actor.userId(), scopeKey, idempotencyKey);
                if (prior != null) {
                    log.info("交互答复幂等复用原回执 interactionId={} commandId={}",
                            interactionId, prior.commandId());
                    return new CommandReceipt(prior.commandId(), prior.taskId(),
                            prior.status(), null, null);
                }
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "交互已答复，不能重复处理");
        }
        if (row.version() != expectedVersion) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "交互版本已变化，请刷新后重试");
        }
        if (row.expiresAt() != null && row.expiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "交互答复已过期");
        }

        // 审批可能触发外部写入，必须先确认交互仍开放且版本有效。
        if (KIND_APPROVAL.equals(row.kind())) {
            answerText = resolveApproval(actor, row, approve);
        }

        String commandId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("interactionId", interactionId);
        payload.put("requestId", row.frameworkRequestId());
        payload.put("kind", row.kind());
        payload.put("answerText", answerText);
        String payloadHash = ContentDigests.sha256(RuntimePayloads.toJson(objectMapper, payload));
        commandMapper.insertControl(commandId, actor.userId(), scopeKey, idempotencyKey, payloadHash,
                row.taskId(), row.bindingId(), "RESPOND",
                RuntimePayloads.toJson(objectMapper, payload),
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(5));
        if (interactionMapper.markResponded(actor.userId(), interactionId, row.version(),
                answerJsonText, actor.username()) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "交互答复并发冲突，请刷新后重试");
        }
        log.info("交互答复已受理 interactionId={} kind={} commandId={} approve={}",
                interactionId, row.kind(), commandId, approve);
        return new CommandReceipt(commandId, row.taskId(), "PENDING", null, null);
    }

    /**
     * 审批决定的平台处置：批准派发连接器，拒绝取消操作；已成功操作复用结果实现重放幂等。
     */
    private String resolveApproval(AuthView actor, InteractionRow row, boolean approve) {
        OperationRow operation = operationMapper.findOwned(actor.userId(), row.operationId());
        if (operation == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "审批对应的操作不存在");
        }
        if (!approve) {
            if (OperationStatus.WAITING_APPROVAL.name().equals(operation.status())) {
                operationMapper.cancelWaitingApproval(operation.userId(), operation.operationId(),
                        operation.version(), RuntimePayloads.toJson(objectMapper,
                                Map.of("denied", true)), actor.userId());
                log.info("写入操作被拒绝取消 operationId={}", operation.operationId());
            } else if (!OperationStatus.CANCELLED.name().equals(operation.status())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "操作已不可拒绝");
            }
            return "用户拒绝执行该写入操作，未产生外部变更。请向用户确认后续安排。";
        }
        if (OperationStatus.SUCCEEDED.name().equals(operation.status())) {
            // 外部调用成功后若答复事务回滚，重放时仍需补结预算；结算按预留状态幂等。
            operationService.settle(operation, TICKET_RESERVED_UNITS);
            return "用户已批准，写入执行成功: " + operationExecutor.resultOf(operation);
        }
        if (!OperationStatus.WAITING_APPROVAL.name().equals(operation.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "操作当前状态不可批准（" + operation.status() + "）");
        }
        String resultJson;
        if (TOOL_MOCK_TICKET.equals(operation.toolKey())) {
            Map<String, Object> args = readArguments(operation.argumentsJson());
            resultJson = operationExecutor.execute(operation,
                    externalKey -> mockTicketConnector.createTicket(operation.userId(), externalKey,
                            String.valueOf(args.get("title")), String.valueOf(args.get("detail"))));
        } else {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "工具没有注册的审批执行器");
        }
        operationService.settle(operation, TICKET_RESERVED_UNITS);
        return "用户已批准，写入执行成功: " + resultJson;
    }

    /**
     * 结构化 JSON 比较；response_json 列由 MySQL 规范化格式，不能按原文比较。
     */
    private boolean sameJson(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        try {
            return RuntimePayloads.readJson(objectMapper, left)
                    .equals(RuntimePayloads.readJson(objectMapper, right));
        } catch (Exception exception) {
            return false;
        }
    }

    private String buildApprovalQuestion(String toolName, OperationRow operation) {
        Map<String, Object> args = readArguments(operation.argumentsJson());
        StringBuilder question = new StringBuilder("请求审批：").append(toolName);
        if (operation.businessIntentKey() != null) {
            question.append("（意图 ").append(operation.businessIntentKey()).append("）");
        }
        question.append("。参数：").append(args);
        return question.length() > 4000 ? question.substring(0, 4000) : question.toString();
    }

    private String fixedApprovalSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "kind", Map.of("const", "APPROVAL"),
                "approve", Map.of("type", "boolean"),
                "confirmedRequestHash", Map.of("type", "string")));
        schema.put("required", List.of("kind", "approve", "confirmedRequestHash"));
        return RuntimePayloads.toJson(objectMapper, schema);
    }

    private String extractQuestion(Map<String, Object> call) {
        Object input = call.get("input");
        if (input instanceof Map<?, ?> inputMap) {
            Object question = inputMap.get("question");
            if (question instanceof String text && !text.isBlank()) {
                return text.length() > 4000 ? text.substring(0, 4000) : text;
            }
        }
        throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "追问缺少问题文本");
    }

    private String extractAnswerSchema(Map<String, Object> call) {
        Object input = call.get("input");
        if (input instanceof Map<?, ?> inputMap
                && inputMap.get("answerSchema") instanceof Map<?, ?> schemaMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) schemaMap;
            return RuntimePayloads.toJson(objectMapper, schema);
        }
        return RuntimePayloads.toJson(objectMapper, Map.of("type", "object"));
    }

    private Map<String, Object> readArguments(String argumentsJson) {
        try {
            JsonNode node = RuntimePayloads.readJson(objectMapper, argumentsJson);
            return objectMapper.convertValue(node, objectMapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
        } catch (Exception exception) {
            throw new IllegalStateException("操作参数无法解析", exception);
        }
    }
}
