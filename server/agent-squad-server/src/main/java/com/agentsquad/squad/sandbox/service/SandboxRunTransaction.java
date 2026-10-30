package com.agentsquad.squad.sandbox.service;

import com.agentsquad.squad.artifact.dto.ArtifactStageView;
import com.agentsquad.squad.artifact.service.ArtifactDeliveryService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.governance.service.BudgetService;
import com.agentsquad.squad.sandbox.domain.SandboxExecutionContext;
import com.agentsquad.squad.sandbox.domain.SandboxRunRow;
import com.agentsquad.squad.sandbox.domain.SandboxDispatchClaim;
import com.agentsquad.squad.sandbox.dto.SandboxDriverResult;
import com.agentsquad.squad.sandbox.dto.SandboxExecuteRequest;
import com.agentsquad.squad.sandbox.dto.SandboxExecutionResult;
import com.agentsquad.squad.sandbox.repository.SandboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 沙箱派发前与结果提交后的独立事务边界。
 * <p>
 * IN_FLIGHT和预算先提交才执行容器；进程失联后保留占额，不自动重发原执行。
 */
@Service
public class SandboxRunTransaction {
    private static final long UNITS_PER_SECOND = 1000;

    @Resource
    private SandboxAccessService access;

    @Resource
    private SandboxMapper mapper;

    @Resource
    private BudgetService budget;

    @Resource
    private ArtifactDeliveryService delivery;

    @Resource
    private ObjectMapper json;

    @Transactional
    public SandboxDispatchClaim begin(SandboxExecutionContext context, SandboxExecuteRequest request, String hash) {
        access.requireCurrent(context);
        SandboxRunRow prior = mapper.prior(context.userId(), context.bindingId(), request.logicalKey(),
                context.turnId(), context.toolCallId());
        if (prior != null) {
            if (!hash.equals(prior.requestHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同沙箱调用键的输入不一致");
            }
            return new SandboxDispatchClaim(prior, false);
        }
        String id = UUID.randomUUID().toString();
        budget.reserve(context.userId(), context.scopeId(), usageKey(id), "SANDBOX",
                request.timeoutSeconds() * UNITS_PER_SECOND, "sandbox");
        mapper.insert(id, context.userId(), context.scopeId(), context.bindingId(), request.logicalKey(),
                context.turnId(), context.toolCallId(), hash,
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(request.timeoutSeconds() + 120));
        return new SandboxDispatchClaim(new SandboxRunRow(id, context.userId(), context.scopeId(), context.bindingId(), hash, "IN_FLIGHT", null), true);
    }

    @Transactional
    public SandboxExecutionResult finish(SandboxExecutionContext context, String executionId,
                                         SandboxExecuteRequest request, SandboxDriverResult result) {
        access.requireCurrent(context);
        List<ArtifactStageView> stages = new ArrayList<>();
        if (result.exitCode() == 0) {
            for (var output : result.outputs()) {
                stages.add(delivery.stage(context, executionId, output.path(), output.content()));
            }
        }
        String status = result.exitCode() == 0 ? "SUCCEEDED" : "FAILED";
        SandboxExecutionResult outcome = new SandboxExecutionResult(executionId, status, result.exitCode(),
                result.stdout(), result.stderr(), result.truncated(), List.copyOf(stages));
        if (mapper.finish(context.userId(), executionId, status, write(outcome)) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "沙箱结果已经失效，不能重复发布");
        }
        // 按固定时限价格保守出账，不能用不可信脚本自报的时间减少用量。
        budget.settle(context.userId(), context.scopeId(), usageKey(executionId),
                request.timeoutSeconds() * UNITS_PER_SECOND);
        return outcome;
    }

    @Transactional
    public void unknown(SandboxExecutionContext context, String executionId) {
        budget.markUnknown(context.userId(), context.scopeId(), usageKey(executionId));
        mapper.finish(context.userId(), executionId, "UNKNOWN", null);
    }

    /**
     * 重启后把失联执行和预算一起转为待核对，锁顺序保持Scope、预算、执行账本。
     * 已结束执行的预算结算不会被覆盖，也不会通过创建新容器重发。
     */
    @Transactional
    public int expire(SandboxRunRow candidate) {
        budget.markUnknown(candidate.userId(), candidate.scopeId(), usageKey(candidate.executionId()));
        SandboxRunRow current = mapper.find(candidate.userId(), candidate.executionId());
        if (current == null || !"IN_FLIGHT".equals(current.status())) {
            return 0;
        }
        return mapper.expireOne(candidate.userId(), candidate.executionId());
    }

    public SandboxExecutionResult replay(SandboxRunRow prior) {
        if (!("SUCCEEDED".equals(prior.status()) || "FAILED".equals(prior.status())) || prior.resultJson() == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "沙箱执行已受理但结果待核对，不能自动重放");
        }
        try {
            return json.treeToValue(com.agentsquad.squad.common.util.RuntimePayloads.readJson(json, prior.resultJson()), SandboxExecutionResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("沙箱持久结果损坏", exception);
        }
    }

    private String write(SandboxExecutionResult result) {
        try {
            return json.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("沙箱结果序列化失败", exception);
        }
    }

    private String usageKey(String executionId) {
        return "sandbox:" + executionId;
    }
}
