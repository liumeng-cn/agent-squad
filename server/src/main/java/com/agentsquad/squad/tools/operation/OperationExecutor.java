package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
import com.agentsquad.squad.runtime.service.RuntimeExceptionCode;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.domain.OperationStatus;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.agentsquad.squad.runtime.util.RuntimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * 外部操作执行器。每个状态迁移都是单条 CAS 语句，自动提交即独立持久：
 * IN_FLIGHT 提交后发送前崩溃，操作留在 IN_FLIGHT，由核对流程转 UNKNOWN；
 * 结果出账必须携带同一执行器 owner/fence，迟到结果不能落到其他记录上。
 * 当前为本地单执行器实现，owner/fence 只做诊断；多实例 guard 属后续阶段。
 */
@Service
@Slf4j
public class OperationExecutor {

    private static final long LEASE_SECONDS = 60;

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private ObjectMapper objectMapper;

    private final String instanceId = "op-" + UUID.randomUUID().toString().substring(0, 8);

    @Value("${agentsquad.fault.after-inflight-exit:false}")
    private boolean faultAfterInflightExit;

    /**
     * 执行账本操作并返回连接器结果 JSON；调用方决定连接器行为。
     */
    public String execute(OperationRow operation, ExternalCall externalCall) {
        String externalKey = "mock:" + operation.operationId();
        markInFlight(operation, externalKey);
        if (faultAfterInflightExit) {
            log.error("故障注入：IN_FLIGHT 已持久，发送前进程退出 operationId={}", operation.operationId());
            Runtime.getRuntime().halt(137);
        }
        String resultJson;
        try {
            resultJson = externalCall.call(externalKey);
        } catch (Exception exception) {
            if (!completeAsFailed(operation, exception)) {
                markUnknown(operation);
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "外部调用失败，操作记为 FAILED");
        }
        if (!completeAsSucceeded(operation, resultJson)) {
            markUnknown(operation);
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "操作结果出账失败，转 UNKNOWN 待核对");
        }
        log.info("操作派发完成 operationId={} status={}", operation.operationId(), OperationStatus.SUCCEEDED);
        return resultJson;
    }

    /**
     * 从已成功账本回收结果（审批重放路径），不再调用连接器。
     */
    public String resultOf(OperationRow operation) {
        if (!OperationStatus.SUCCEEDED.name().equals(operation.status()) || operation.resultJson() == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "操作没有可复用的成功结果");
        }
        return operation.resultJson();
    }

    /**
     * 置 IN_FLIGHT 并写执行器租约；单条 CAS 自动提交，即 IN_FLIGHT 持久点。
     * 租约按 UTC 写入，与失联扫描的比较基准一致。
     */
    public void markInFlight(OperationRow operation, String externalKey) {
        LocalDateTime leaseUntil = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS);
        int inflight = operationMapper.markInFlight(operation.userId(), operation.operationId(),
                operation.version(), instanceId, 1L, leaseUntil, externalKey);
        if (inflight != 1) {
            OperationRow current = operationMapper.findOwned(operation.userId(), operation.operationId());
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "操作状态已变化，不能重复派发（当前 " + current.status() + "）");
        }
    }

    /**
     * 成功出账；返回 false 表示执行器 CAS 竞争，调用方转 UNKNOWN。
     */
    public boolean completeAsSucceeded(OperationRow operation, String resultJson) {
        return operationMapper.completeInFlight(operation.userId(), operation.operationId(),
                operation.version() + 1, instanceId, 1L, OperationStatus.SUCCEEDED.name(),
                resultJson, instanceId) == 1;
    }

    /**
     * 失败出账；连接器异常只留分类信息，不落原始异常正文。
     */
    public boolean completeAsFailed(OperationRow operation, Exception cause) {
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return operationMapper.completeInFlight(operation.userId(), operation.operationId(),
                operation.version() + 1, instanceId, 1L, OperationStatus.FAILED.name(),
                RuntimePayloads.toJson(objectMapper, Map.of("error", message)), instanceId) == 1;
    }

    /**
     * 结果不明出账；只能由核对流程再推进。
     */
    public void markUnknown(OperationRow operation) {
        operationMapper.markUnknown(operation.userId(), operation.operationId(),
                operation.version() + 1, instanceId);
        log.warn("操作转入 UNKNOWN，等待人工或连接器核对 operationId={}", operation.operationId());
    }
}
