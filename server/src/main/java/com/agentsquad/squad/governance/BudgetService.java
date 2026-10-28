package com.agentsquad.squad.governance;

import com.agentsquad.squad.governance.repository.BudgetMapper;
import com.agentsquad.squad.governance.domain.BudgetAccountRow;
import com.agentsquad.squad.governance.domain.BudgetReservationRow;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import jakarta.annotation.Resource;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.BigInteger;

/**
 * 执行作用域预算预留与结算。
 *
 * <p>同一 usage_key 预留幂等；同账户累计（未结预留 + 已结算）超过上限拒绝新预检。
 * UNKNOWN 预留不自动释放，只能随操作核对出账；所有回复和目标还受同用户全局预算约束，Scope 不会因转任务重置账户。</p>
 */
@Service
@Slf4j
public class BudgetService {

    @Resource
    private BudgetMapper budgetMapper;

    @Resource
    private ExecutionScopeService scopeService;

    @Value("${agentsquad.budget.max-microunits-per-user:50000000}")
    private long userLimitMicrounits;

    /**
     * 按稳定 usage_key 预留预算；重复预留返回原记录，超限抛业务冲突。
     */
    @Transactional
    public void reserve(String userId, String scopeId, String usageKey, String kind,
                        long units, String creatorName) {
        if (units <= 0 || usageKey == null || usageKey.isBlank() || usageKey.length() > 200) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "预算预留参数无效");
        }
        BudgetAccountRow account = lockBudget(userId, scopeId, true);
        BudgetReservationRow existing = budgetMapper.findReservation(userId, scopeId, usageKey);
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
        requireAvailable(userId, scopeId, account, units);
        if (budgetMapper.insert(UUID.randomUUID().toString(), userId, scopeId, usageKey, kind,
                units, creatorName) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "预算预留冲突，请重试");
        }
        log.debug("预算已预留 userId={} scopeId={} usageKey={} units={}", userId, scopeId, usageKey, units);
    }

    /**
     * 每次真实模型请求前固定请求摘要、Profile 和定价版本；未知收费保留原预留。
     */
    @Transactional
    public void reserveModel(String userId, String scopeId, String usageKey,
                             String requestHash, String modelProfileKey, WorkerModelProfile profile) {
        if (usageKey == null || usageKey.isBlank() || usageKey.length() > 200
                || requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "模型预算请求键或摘要无效");
        }
        BudgetAccountRow account = lockBudget(userId, scopeId, true);
        long units = Math.max(1, cost(profile.getMaxInputTokens(), profile.getMaxOutputTokens(), profile));
        BudgetReservationRow prior = budgetMapper.findReservation(userId, scopeId, usageKey);
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
        requireAvailable(userId, scopeId, account, units);
        budgetMapper.insertModel(UUID.randomUUID().toString(), userId, scopeId, usageKey,
                units, modelProfileKey, profile.getPricingVersion(), requestHash);
        log.info("模型预算已预留 scopeId={} usageKey={} profileKey={} pricingVersion={} units={}",
                scopeId, usageKey, modelProfileKey, profile.getPricingVersion(), units);
    }

    /**
     * 仅凭可信模型用量结算；用量超出已预留的 Profile 上限时保留占额并阻断后续执行。
     */
    @Transactional
    public void settleModel(String userId, String scopeId, String usageKey,
                            WorkerModelProfile profile, long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0
                || inputTokens > profile.getMaxInputTokens()
                || outputTokens > profile.getMaxOutputTokens()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "模型用量超出 Profile 上限，预算保留待核对");
        }
        lockBudget(userId, scopeId, false);
        BudgetReservationRow row = budgetMapper.findReservation(userId, scopeId, usageKey);
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
                || budgetMapper.settleModel(userId, scopeId, usageKey, settled,
                inputTokens, outputTokens) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "模型预算不可结算");
        }
        log.info("模型预算已结算 scopeId={} usageKey={} inputTokens={} outputTokens={} units={}",
                scopeId, usageKey, inputTokens, outputTokens, settled);
    }

    /**
     * 模型调用结果或收费不确定时保留原预留，不把它当作免费失败释放。
     */
    @Transactional
    public void markModelUnknown(String userId, String scopeId, String usageKey) {
        lockBudget(userId, scopeId, false);
        budgetMapper.markModelUnknown(userId, scopeId, usageKey);
    }

    private void requireAvailable(String userId, String scopeId, BudgetAccountRow account, long units) {
        long userConsumed = budgetMapper.sumUserConsumed(userId);
        if (userLimitMicrounits <= 0 || userConsumed > userLimitMicrounits
                || units > userLimitMicrounits - userConsumed) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "用户总预算不足，已拒绝新请求");
        }
        long consumed = budgetMapper.sumConsumedUnits(userId, scopeId);
        if (consumed > account.limitMicrounits() || units > account.limitMicrounits() - consumed) {
            log.info("预算预留被拒 userId={} scopeId={} consumed={} requested={} limit={}",
                    userId, scopeId, consumed, units, account.limitMicrounits());
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "执行预算不足，已拒绝新请求");
        }
    }

    /**
     * 会话与实际所有者先锁定，再锁用户总账与共享账户；已知结果结算不被取消或删除吞掉。
     */
    private BudgetAccountRow lockBudget(String userId, String scopeId, boolean forDispatch) {
        scopeService.lockOwned(userId, scopeId, forDispatch);
        budgetMapper.ensureUser(userId);
        budgetMapper.lockUser(userId);
        BudgetAccountRow account = budgetMapper.lockAccount(userId, scopeId);
        if (account == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "执行预算账户不可用");
        }
        return account;
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
    public void settle(String userId, String scopeId, String usageKey, long settledUnits) {
        if (settledUnits < 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "工具预算结算金额无效");
        }
        lockBudget(userId, scopeId, false);
        if (budgetMapper.settle(userId, scopeId, usageKey, settledUnits) == 1) {
            return;
        }
        BudgetReservationRow prior = budgetMapper.findReservation(userId, scopeId, usageKey);
        if (prior == null || "MODEL".equals(prior.kind()) || !"SETTLED".equals(prior.status())
                || prior.settledUnits() != settledUnits) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "工具预算结算超过预留或状态不匹配");
        }
    }

    /**
     * 核对确认成功后按原工具预留额出账；预留缺失或状态不匹配时拒绝，不能无账结算。
     */
    @Transactional
    public void settleReserved(String userId, String scopeId, String usageKey) {
        lockBudget(userId, scopeId, false);
        Long reserved = budgetMapper.findReservedUnits(userId, scopeId, usageKey);
        if (reserved == null || budgetMapper.settle(userId, scopeId, usageKey, reserved) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "待核对工具预算预留不存在或状态不匹配");
        }
        log.info("预算按预留额结算 userId={} scopeId={} usageKey={} units={}",
                userId, scopeId, usageKey, reserved);
    }
}
