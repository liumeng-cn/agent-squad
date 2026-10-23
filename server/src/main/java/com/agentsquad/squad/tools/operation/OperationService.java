package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.governance.BudgetService;
import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
import com.agentsquad.squad.runtime.service.RuntimeExceptionCode;
import com.agentsquad.squad.runtime.util.RuntimePayloads;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.domain.OperationStatus;
import com.agentsquad.squad.tools.operation.domain.ToolExecutionContextRow;
import com.agentsquad.squad.tools.operation.dto.OperationPage;
import com.agentsquad.squad.tools.operation.dto.OperationView;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.agentsquad.squad.runtime.util.ContentDigests.sha256;
import static com.agentsquad.squad.runtime.util.RuntimePayloads.toJson;

/**
 * 外部操作账本预检服务（M07 事务 A）。
 *
 * <p>锁定目标上下文、规范化参数并计算摘要、预留预算、落 PREPARED/WAITING_APPROVAL；
 * 本事务提交前绝不发送外部请求，实际派发由 OperationExecutor 分步独立持久。
 * 原生调用键与业务意图键双路幂等；同意图不同参数直接拒绝。</p>
 */
@Service
@Slf4j
public class OperationService {

    private static final String EFFECT_WRITE = "WRITE";

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private BudgetService budgetService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 预检并创建操作账本记录；原生调用键或业务意图键命中即返回原记录，不产生第二行。
     */
    @Transactional
    public OperationRow prepare(PrepareRequest request) {
        ToolExecutionContextRow context = operationMapper.lockToolContext(request.userId(),
                request.sessionId());
        if (context == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "会话未绑定目标");
        }
        if (context.cancellationRequested() || !("QUEUED".equals(context.taskStatus())
                || "RUNNING".equals(context.taskStatus()))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "目标已取消或结束，不再受理工具操作");
        }

        OperationRow existing = operationMapper.findByNativeCall(request.userId(), context.bindingId(),
                request.frameworkTurnId(), request.toolCallId());
        if (existing != null) {
            log.info("工具调用幂等复用原操作 operationId={} toolCallId={}",
                    existing.operationId(), request.toolCallId());
            return existing;
        }

        String requestHash = sha256(toJson(objectMapper, canonicalArguments(request.arguments())));
        if (EFFECT_WRITE.equals(request.effect())) {
            if (request.businessIntentKey() == null || request.businessIntentKey().isBlank()
                    || request.businessIntentKey().length() > 160) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "写入操作缺少业务幂等键");
            }
            OperationRow intent = operationMapper.findByBusinessIntent(request.userId(),
                    context.taskId(), request.toolKey(), request.businessIntentKey());
            if (intent != null) {
                if (!intent.requestHash().equals(requestHash)) {
                    throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                            "同业务意图已用不同参数提交，拒绝重复写入");
                }
                log.info("业务意图幂等复用原操作 operationId={} intentKey={}",
                        intent.operationId(), request.businessIntentKey());
                return intent;
            }
        }

        budgetService.reserve(request.userId(), context.taskId(),
                usageKey(request), "TOOL", request.reservedUnits(), request.creatorName());

        String operationId = UUID.randomUUID().toString();
        String status = request.requiresApproval() ? OperationStatus.WAITING_APPROVAL.name()
                : OperationStatus.PREPARED.name();
        operationMapper.insert(operationId, request.userId(), context.taskId(), null,
                context.bindingId(), request.frameworkTurnId(), request.toolCallId(),
                request.toolKey(), request.toolVersion(), request.businessIntentKey(),
                request.effect(), request.connectionId(), request.connectionRevision(),
                0L, requestHash, toJson(objectMapper, canonicalArguments(request.arguments())),
                status, request.creatorName());
        log.info("操作预检完成 operationId={} taskId={} toolKey={} effect={} status={} hash={}",
                operationId, context.taskId(), request.toolKey(), request.effect(), status,
                requestHash.substring(0, 8));
        return operationMapper.findOwned(request.userId(), operationId);
    }

    /**
     * 分页读取目标的操作视图；游标取最后一条操作的存储主键。
     */
    public OperationPage listPage(String userId, String taskId, String cursor, int limit) {
        Long cursorId = parseCursor(cursor);
        List<OperationRow> rows = operationMapper.listByTask(userId, taskId, cursorId,
                Math.min(Math.max(limit, 1), 100) + 1);
        boolean hasMore = rows.size() > Math.min(Math.max(limit, 1), 100);
        List<OperationView> items = rows.stream()
                .limit(Math.min(Math.max(limit, 1), 100))
                .map(this::viewOf)
                .toList();
        String nextCursor = null;
        if (hasMore) {
            Long lastCursorId = operationMapper.cursorIdOf(userId,
                    rows.get(items.size() - 1).operationId());
            nextCursor = lastCursorId == null ? null : String.valueOf(lastCursorId);
        }
        return new OperationPage(items, nextCursor);
    }

    /**
     * 读取当前用户单个操作视图；不存在统一按 NOT_FOUND 处理。
     */
    public OperationView getOwned(String userId, String operationId) {
        OperationRow row = operationMapper.findOwned(userId, operationId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "操作不存在");
        }
        return viewOf(row);
    }

    /**
     * 账本行映射为对外视图；arguments/result 解析为 JSON 供核对，敏感连接凭据不出现在视图中。
     */
    public OperationView viewOf(OperationRow row) {
        return new OperationView(row.operationId(), row.taskId(), row.bindingId(),
                row.frameworkTurnId(), row.toolCallId(), row.toolKey(), row.toolVersion(),
                row.businessIntentKey(), row.effect(), row.connectionId(),
                row.connectionRevision(), row.requestHash(),
                parseJsonOrNull(row.argumentsJson()), row.status(), row.version(),
                row.externalKey(), parseJsonOrNull(row.resultJson()));
    }

    private Object parseJsonOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return RuntimePayloads.readJson(objectMapper, json);
        } catch (Exception exception) {
            log.warn("操作载荷无法解析，按空视图返回");
            return null;
        }
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "cursor 游标无效");
        }
    }

    /**
     * 结算该操作的预算预留；金额由调用方按工具声明传入。
     */
    public void settle(OperationRow operation, long settledUnits) {
        budgetService.settle(operation.userId(), operation.taskId(), usageKeyOf(operation),
                settledUnits);
    }

    /**
     * 操作的预算稳定键；核对流程复用同一派生规则，避免两处口径漂移。
     */
    public String usageKeyOf(OperationRow operation) {
        return operation.toolKey() + ":" + (operation.businessIntentKey() == null
                ? operation.toolCallId() : operation.businessIntentKey());
    }

    private String usageKey(PrepareRequest request) {
        return request.toolKey() + ":" + (request.businessIntentKey() == null
                ? request.toolCallId() : request.businessIntentKey());
    }

    private Map<String, Object> canonicalArguments(Map<String, Object> arguments) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        arguments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }
}
