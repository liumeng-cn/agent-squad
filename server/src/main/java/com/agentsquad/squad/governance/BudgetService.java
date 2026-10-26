package com.agentsquad.squad.governance;

import com.agentsquad.squad.governance.repository.BudgetMapper;
import com.agentsquad.squad.governance.domain.TaskBudgetRow;
import com.agentsquad.squad.governance.domain.BudgetReservationRow;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.BigInteger;

/**
 * 目标级预算预留与结算（D2 基础 gate）。
 *
 * <p>同一 usage_key 预留幂等；同任务累计（未结预留 + 已结算）超过上限拒绝新预检。
 * UNKNOWN 预留不自动释放，只能随操作核对出账；限制为本地单任务的简单闸门，
 * 全局配额与审计属后续治理模块。</p>
 */
@Service
@Slf4j
public class BudgetService {

    @Resource
    private BudgetMapper budgetMapper;

    /**
     * 按稳定 usage_key 预留预算；重复预留返回原记录，超限抛业务冲突。
     */
    @Transactional
    public void reserve(String userId, String taskId, String usageKey, String kind,
                        long units, String creatorName) {
        if (units <= 0 || usageKey == null || usageKey.isBlank() || usageKey.length() > 200) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "预算预留参数无效");
        }
        TaskBudgetRow taskBudget = budgetMapper.lockTask(userId, taskId);
        if (taskBudget == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "预算所属目标不存在");
        }
        BudgetReservationRow existing = budgetMapper.findReservation(userId, taskId, usageKey);
        if (existing != null) {
            if (!kind.equals(existing.kind()) || units != existing.reservedUnits()) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "同键预算预留内容不一致");
            }
            if ("UNKNOWN".equals(existing.status())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "同键预算仍在核对中，不能重复预留");
            }
            return;
        }
        requireAvailable(userId, taskId, taskBudget, units);
        if (budgetMapper.insert(UUID.randomUUID().toString(), userId, taskId, usageKey, kind,
                units, creatorName) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "预算预留冲突，请重试");
        }
        log.debug("预算已预留 userId={} taskId={} usageKey={} units={}", userId, taskId, usageKey, units);
    }

    /**
     * 每次真实模型请求前固定请求摘要、Profile 和定价版本；未知收费保留原预留。
     */
    @Transactional
    public void reserveModel(String userId, String taskId, String usageKey,
                             String requestHash, String modelProfileKey, WorkerModelProfile profile) {
        if (usageKey == null || usageKey.isBlank() || usageKey.length() > 200
                || requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "模型预算请求键或摘要无效");
        }
        TaskBudgetRow taskBudget = budgetMapper.lockTask(userId, taskId);
        if (taskBudget == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "预算所属目标不存在");
        }
        long units = Math.max(1, cost(profile.getMaxInputTokens(), profile.getMaxOutputTokens(), profile));
        BudgetReservationRow prior = budgetMapper.findReservation(userId, taskId, usageKey);
        if (prior != null) {
            if (!"MODEL".equals(prior.kind()) || prior.reservedUnits() != units
                    || !modelProfileKey.equals(prior.modelProfileKey())
                    || !profile.getPricingVersion().equals(prior.pricingVersion())
                    || !requestHash.equals(prior.requestHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "同键模型预算内容不一致");
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "模型请求已有预留，须核对原请求而不能再次调用");
        }
        requireAvailable(userId, taskId, taskBudget, units);
        budgetMapper.insertModel(UUID.randomUUID().toString(), userId, taskId, usageKey,
                units, modelProfileKey, profile.getPricingVersion(), requestHash);
        log.info("模型预算已预留 taskId={} usageKey={} profileKey={} pricingVersion={} units={}",
                taskId, usageKey, modelProfileKey, profile.getPricingVersion(), units);
    }

    /**
     * 仅凭可信模型用量结算；用量超出已预留的 Profile 上限时保留占额并阻断后续执行。
     */
    @Transactional
    public void settleModel(String userId, String taskId, String usageKey,
                            WorkerModelProfile profile, long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0
                || inputTokens > profile.getMaxInputTokens()
                || outputTokens > profile.getMaxOutputTokens()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "模型用量超出 Profile 上限，预算保留待核对");
        }
        TaskBudgetRow taskBudget = budgetMapper.lockTask(userId, taskId);
        if (taskBudget == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "预算所属目标不存在");
        }
        BudgetReservationRow row = budgetMapper.findReservation(userId, taskId, usageKey);
        if (row == null || !"MODEL".equals(row.kind())
                || !profile.getPricingVersion().equals(row.pricingVersion())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "模型预算预留或定价版本不匹配");
        }
        long settled = cost(inputTokens, outputTokens, profile);
        if ("SETTLED".equals(row.status())) {
            if (row.settledUnits() == settled && row.inputTokens() == inputTokens
                    && row.outputTokens() == outputTokens) {
                return;
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "模型用量重复结算不一致");
        }
        if (!"RESERVED".equals(row.status()) || settled > row.reservedUnits()
                || budgetMapper.settleModel(userId, taskId, usageKey, settled,
                inputTokens, outputTokens) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "模型预算不可结算");
        }
        log.info("模型预算已结算 taskId={} usageKey={} inputTokens={} outputTokens={} units={}",
                taskId, usageKey, inputTokens, outputTokens, settled);
    }

    /**
     * 模型调用结果或收费不确定时保留原预留，不把它当作免费失败释放。
     */
    @Transactional
    public void markModelUnknown(String userId, String taskId, String usageKey) {
        budgetMapper.markModelUnknown(userId, taskId, usageKey);
    }

    private void requireAvailable(String userId, String taskId, TaskBudgetRow taskBudget, long units) {
        long consumed = budgetMapper.sumConsumedUnits(userId, taskId);
        if (consumed > taskBudget.limitMicrounits() || units > taskBudget.limitMicrounits() - consumed) {
            log.info("预算预留被拒 userId={} taskId={} consumed={} requested={} limit={}",
                    userId, taskId, consumed, units, taskBudget.limitMicrounits());
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "目标预算不足，已拒绝新的外部操作");
        }
    }

    private long cost(long inputTokens, long outputTokens, WorkerModelProfile profile) {
        BigInteger total = BigInteger.valueOf(inputTokens)
                .multiply(BigInteger.valueOf(profile.getInputMicrounitsPerMillionTokens()))
                .add(BigInteger.valueOf(outputTokens)
                        .multiply(BigInteger.valueOf(profile.getOutputMicrounitsPerMillionTokens())));
        return total.add(BigInteger.valueOf(999999)).divide(BigInteger.valueOf(1000000)).longValueExact();
    }

    /**
     * 结算已确定消耗；UNKNOWN 预留不动，由核对流程处理。
     */
    @Transactional
    public void settle(String userId, String taskId, String usageKey, long settledUnits) {
        if (settledUnits < 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "工具预算结算金额无效");
        }
        if (budgetMapper.settle(userId, taskId, usageKey, settledUnits) == 1) {
            return;
        }
        BudgetReservationRow prior = budgetMapper.findReservation(userId, taskId, usageKey);
        if (prior == null || "MODEL".equals(prior.kind()) || !"SETTLED".equals(prior.status())
                || prior.settledUnits() != settledUnits) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "工具预算结算超过预留或状态不匹配");
        }
    }

    /**
     * 核对确认成功后按原预留额出账；找不到预留记录时按 0 出账不影响主流程。
     */
    @Transactional
    public void settleReserved(String userId, String taskId, String usageKey) {
        Long reserved = budgetMapper.findReservedUnits(userId, taskId, usageKey);
        if (reserved == null || budgetMapper.settle(userId, taskId, usageKey, reserved) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "待核对工具预算预留不存在或状态不匹配");
        }
        log.info("预算按预留额结算 userId={} taskId={} usageKey={} units={}",
                userId, taskId, usageKey, reserved);
    }
}
