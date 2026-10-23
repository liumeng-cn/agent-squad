package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.governance.BudgetService;
import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
import com.agentsquad.squad.runtime.service.RuntimeExceptionCode;
import com.agentsquad.squad.runtime.util.RuntimePayloads;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.domain.OperationStatus;
import com.agentsquad.squad.tools.operation.dto.OperationView;
import com.agentsquad.squad.tools.operation.repository.MockTicketMapper;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作账本核对（D2）：IN_FLIGHT 失联转 UNKNOWN 后，只能由核对出账。
 *
 * <p>人工结论仅限 CONFIRMED_SUCCEEDED / CONFIRMED_NOT_EXECUTED / UNRESOLVED；
 * UNRESOLVED 记录证据但保持 UNKNOWN，不允许"忽略"清掉未知事实。mock_ticket_create
 * 的 KEY 语义支持按 external_key 查证：外部已有同键工单即确证成功。
 * 执行器 CAS 全部要求 IN_FLIGHT，核对后的状态不会被迟到结果覆盖。</p>
 */
@Service
@Slf4j
public class OperationReconcileService {

    private static final String OUTCOME_SUCCEEDED = "CONFIRMED_SUCCEEDED";
    private static final String OUTCOME_NOT_EXECUTED = "CONFIRMED_NOT_EXECUTED";
    private static final String OUTCOME_UNRESOLVED = "UNRESOLVED";

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private MockTicketMapper mockTicketMapper;

    @Resource
    private BudgetService budgetService;

    @Resource
    private OperationService operationService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 核对 UNKNOWN 操作并出账；确认成功按预留额结算，确认未执行释放预留。
     */
    @Transactional
    public OperationView reconcile(String userId, String operationId, String outcome, String note) {
        OperationRow row = operationMapper.findOwned(userId, operationId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "操作不存在");
        }
        if (!OperationStatus.UNKNOWN.name().equals(row.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "只有 UNKNOWN 操作可以核对（当前 " + row.status() + "）");
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("reconciledBy", userId);
        evidence.put("note", note);
        switch (outcome) {
            case OUTCOME_SUCCEEDED -> reconcileSucceeded(userId, row, evidence);
            case OUTCOME_NOT_EXECUTED -> reconcileNotExecuted(userId, row, evidence);
            case OUTCOME_UNRESOLVED -> recordUnresolved(userId, row, evidence);
            default -> throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                    "核对结论只能是 CONFIRMED_SUCCEEDED / CONFIRMED_NOT_EXECUTED / UNRESOLVED");
        }
        return operationService.getOwned(userId, operationId);
    }

    /**
     * 确认已执行：优先按 KEY 语义查证外部系统；查不到时接受人工证据。
     */
    private void reconcileSucceeded(String userId, OperationRow row, Map<String, Object> evidence) {
        String connectorResult = lookupExternalTicket(userId, row);
        if (connectorResult != null) {
            evidence.put("source", "CONNECTOR_KEY_LOOKUP");
            evidence.put("externalResult", parseJsonQuietly(connectorResult));
        } else {
            evidence.put("source", "HUMAN_ATTESTED");
        }
        if (operationMapper.reconcileOutcome(userId, row.operationId(), row.version(),
                OperationStatus.SUCCEEDED.name(), RuntimePayloads.toJson(objectMapper, evidence),
                userId) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "核对并发冲突，请刷新后重试");
        }
        budgetService.settleReserved(row.userId(), row.taskId(),
                operationService.usageKeyOf(row));
        log.info("操作核对为已成功 operationId={} source={}", row.operationId(), evidence.get("source"));
    }

    /**
     * 确认未执行：出账为 FAILED 并释放预算；重试需重新走预检与审批。
     */
    private void reconcileNotExecuted(String userId, OperationRow row, Map<String, Object> evidence) {
        evidence.put("source", "HUMAN_ATTESTED");
        if (operationMapper.reconcileOutcome(userId, row.operationId(), row.version(),
                OperationStatus.FAILED.name(), RuntimePayloads.toJson(objectMapper, evidence),
                userId) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "核对并发冲突，请刷新后重试");
        }
        budgetService.settle(row.userId(), row.taskId(), operationService.usageKeyOf(row), 0L);
        log.info("操作核对为未执行 operationId={}", row.operationId());
    }

    /**
     * 无法确认：只记录证据，状态保持 UNKNOWN，等待更多事实。
     */
    private void recordUnresolved(String userId, OperationRow row, Map<String, Object> evidence) {
        evidence.put("source", "HUMAN_UNRESOLVED");
        evidence.put("stillUnknown", true);
        if (operationMapper.recordUnresolved(userId, row.operationId(), row.version(),
                RuntimePayloads.toJson(objectMapper, evidence), userId) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "核对并发冲突，请刷新后重试");
        }
        log.info("操作核对暂无结论，保持 UNKNOWN operationId={}", row.operationId());
    }

    /**
     * 按连接器 KEY 幂等键查证模拟工单是否已存在；非工单写入或键不存在返回 null。
     */
    private String lookupExternalTicket(String userId, OperationRow row) {
        if (!"mock_ticket_create".equals(row.toolKey()) || row.externalKey() == null) {
            return null;
        }
        if (mockTicketMapper.countByKey(userId, row.externalKey()) == 0) {
            return null;
        }
        return RuntimePayloads.toJson(objectMapper, Map.of(
                "ticketRef", row.externalKey(), "duplicated", true));
    }

    private Object parseJsonQuietly(String json) {
        try {
            return RuntimePayloads.readJson(objectMapper, json);
        } catch (Exception exception) {
            return json;
        }
    }
}
