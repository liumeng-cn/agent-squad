package com.agentsquad.squad.service;

import com.agentsquad.squad.model.bo.ExternalCallBO;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.po.OperationPO;
import com.agentsquad.squad.enums.OperationStatus;
import com.agentsquad.squad.mapper.OperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
public class OperationExecutorService {
    @Resource
    private UserStatusGuardService userStatus;

    private static final long LEASE_SECONDS = 60;

    @Resource
    private OperationUnknownService unknowns;

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private OperationDispatchService dispatchService;

    @Resource
    private ObjectMapper objectMapper;

    private final String instanceId = "op-" + UUID.randomUUID().toString().substring(0, 8);

    /**
     * 执行账本操作并返回连接器结果 JSON；暂停调用方事务，确保 IN_FLIGHT 在外部调用前独立提交。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String execute(OperationPO operation, ExternalCallBO externalCall) {
        userStatus.requireActive(operation.getUserId());
        String externalKey = "mock:" + operation.getOperationId();
        markInFlight(operation, externalKey);
        String resultJson;
        try {
            resultJson = externalCall.call(externalKey);
        } catch (Exception exception) {
            if ("WRITE".equals(operation.getEffect())) {
                // 发送后的异常不能证明外部没有执行，必须保留未知事实，禁止重放写入。
                markUnknown(operation);
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "外部写入结果待核对，不会自动重复执行");
            }
            if (!completeAsFailed(operation, exception)) {
                markUnknown(operation);
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "外部调用失败，操作记为 FAILED");
        }
        if (!completeAsSucceeded(operation, resultJson)) {
            markUnknown(operation);
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "操作结果出账失败，转 UNKNOWN 待核对");
        }
        log.info("操作派发完成 operationId={} status={}", operation.getOperationId(), OperationStatus.SUCCEEDED);
        return resultJson;
    }

    /**
     * 从已成功账本回收结果（审批重放路径），不再调用连接器。
     */
    public String resultOf(OperationPO operation) {
        if (!OperationStatus.SUCCEEDED.name().equals(operation.getStatus()) || operation.getResultJson() == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "操作没有可复用的成功结果");
        }
        return operation.getResultJson();
    }

    /**
     * 置 IN_FLIGHT 并写执行器租约；单条 CAS 自动提交，即 IN_FLIGHT 持久点。
     * 租约按 UTC 写入，与失联扫描的比较基准一致。
     */
    public void markInFlight(OperationPO operation, String externalKey) {
        LocalDateTime leaseUntil = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS);
        int inflight = dispatchService.begin(operation, instanceId, 1L, leaseUntil, externalKey);
        if (inflight != 1) {
            OperationPO current = operationMapper.findOwned(operation.getUserId(), operation.getOperationId());
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "操作状态已变化，不能重复派发（当前 " + current.getStatus() + "）");
        }
    }

    /**
     * 成功出账；返回 false 表示执行器 CAS 竞争，调用方转 UNKNOWN。
     */
    public boolean completeAsSucceeded(OperationPO operation, String resultJson) {
        return operationMapper.completeInFlight(operation.getUserId(), operation.getOperationId(),
                operation.getVersion() + 1, instanceId, 1L, OperationStatus.SUCCEEDED.name(),
                resultJson, instanceId) == 1;
    }

    /**
     * 失败出账；连接器异常只留分类信息，不落原始异常正文。
     */
    public boolean completeAsFailed(OperationPO operation, Exception cause) {
        String message = cause.getClass().getSimpleName();
        return operationMapper.completeInFlight(operation.getUserId(), operation.getOperationId(),
                operation.getVersion() + 1, instanceId, 1L, OperationStatus.FAILED.name(),
                RuntimePayloads.toJson(objectMapper, Map.of("error", message)), instanceId) == 1;
    }

    /**
     * 结果不明出账；只能由核对流程再推进。
     */
    public void markUnknown(OperationPO operation) {
        if (unknowns.mark(operation, operation.getVersion() + 1, instanceId)) {
            log.warn("操作转入 UNKNOWN，等待人工或连接器核对 operationId={}", operation.getOperationId());
        }
    }
}
