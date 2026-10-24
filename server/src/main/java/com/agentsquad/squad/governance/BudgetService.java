package com.agentsquad.squad.governance;

import com.agentsquad.squad.governance.repository.BudgetMapper;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Value("${agentsquad.budget.max-tool-units-per-task:5000000}")
    private long maxToolUnitsPerTask;

    /**
     * 按稳定 usage_key 预留预算；重复预留返回原记录，超限抛业务冲突。
     */
    @Transactional
    public void reserve(String userId, String taskId, String usageKey, String kind,
                        long units, String creatorName) {
        String existing = budgetMapper.findStatus(userId, taskId, usageKey);
        if (existing != null) {
            if ("UNKNOWN".equals(existing)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "同键预算仍在核对中，不能重复预留");
            }
            return;
        }
        long consumed = budgetMapper.sumConsumedUnits(userId, taskId);
        if (consumed + units > maxToolUnitsPerTask) {
            log.info("预算预留被拒 userId={} taskId={} consumed={} requested={} limit={}",
                    userId, taskId, consumed, units, maxToolUnitsPerTask);
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "目标预算不足，已拒绝新的外部操作");
        }
        if (budgetMapper.insert(UUID.randomUUID().toString(), userId, taskId, usageKey, kind,
                units, creatorName) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "预算预留冲突，请重试");
        }
        log.debug("预算已预留 userId={} taskId={} usageKey={} units={}", userId, taskId, usageKey, units);
    }

    /**
     * 结算已确定消耗；UNKNOWN 预留不动，由核对流程处理。
     */
    @Transactional
    public void settle(String userId, String taskId, String usageKey, long settledUnits) {
        budgetMapper.settle(userId, taskId, usageKey, settledUnits);
    }

    /**
     * 核对确认成功后按原预留额出账；找不到预留记录时按 0 出账不影响主流程。
     */
    @Transactional
    public void settleReserved(String userId, String taskId, String usageKey) {
        Long reserved = budgetMapper.findReservedUnits(userId, taskId, usageKey);
        budgetMapper.settle(userId, taskId, usageKey, reserved == null ? 0L : reserved);
        log.info("预算按预留额结算 userId={} taskId={} usageKey={} units={}",
                userId, taskId, usageKey, reserved);
    }
}
