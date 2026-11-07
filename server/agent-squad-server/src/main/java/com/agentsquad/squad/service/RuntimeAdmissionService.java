package com.agentsquad.squad.service;

import com.agentsquad.squad.domain.entity.RuntimeAdmissionSlotDO;
import com.agentsquad.squad.conversation.repository.RuntimeAdmissionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Set;
import java.util.List;

/**
 * 原生运行持久容量管理。
 *
 * 领取新运行命令时在 MySQL 事务内同时占用全局和用户配额，
 * 只在匹配的原生 run/end 已数据库记录时释放；未知结果保守保留，
 * 不把短期命令投递租约视为运行结束事实。
 */
@Service
@Slf4j
public class RuntimeAdmissionService {

    private static final String GLOBAL_POOL = "GLOBAL";
    private static final Set<String> SUBMISSION_ACTIONS = Set.of(
            "SUBMIT", "MESSAGE_SUBMIT", "MASTER_SIGNAL", "TASK_DIRECTIVE");

    private static final Set<String> RECOVERY_ACTIONS = Set.of("RESUME", "RESPOND");

    @Resource
    private RuntimeAdmissionMapper mapper;

    @Value("${agentsquad.delivery.admission.enabled:false}")
    private boolean enabled;

    @Value("${agentsquad.delivery.admission.global-limit:12}")
    private int globalLimit;

    @Value("${agentsquad.delivery.admission.per-user-limit:2}")
    private int perUserLimit;

    @Value("${agentsquad.delivery.admission.audit-max-pools:10}")
    private int auditMaxPools;

    /**
     * 与 CommandClaimService.claim 共用事务；抛出容量异常时整个命令领取回滚。
     * STOP 只负责停止原有运行，不占用新的执行配额。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String userId, String bindingId, String commandId, String action) {
        if (!enabled || "STOP".equals(action)) {
            return;
        }
        requireConfigured();
        if (userId == null || bindingId == null || commandId == null) {
            throw new IllegalStateException("运行占位缺少用户或命令归属");
        }

        // 以领取时的原生投影水位为屏障；只允许后续新 run/start 绑定恢复命令。
        Long claimCursor = mapper.lockBindingCursor(userId, bindingId);
        if (claimCursor == null) {
            throw new IllegalStateException("运行绑定不存在，无法获取容量占位");
        }

        String userPool = poolKey(userId);
        mapper.ensureCounter(GLOBAL_POOL);
        mapper.ensureCounter(userPool);

        Integer total = mapper.lockCounter(GLOBAL_POOL);
        Integer userCount = mapper.lockCounter(userPool);
        RuntimeAdmissionSlotDO existing = mapper.lockSlot(bindingId);

        if (total == null || userCount == null) {
            throw new IllegalStateException("运行容量计数记录不存在");
        }
        if (existing != null && existing.active()) {
            if (userId.equals(existing.userId()) && commandId.equals(existing.commandId())) {
                // 同一命令在提交收据不确定时按原命令 ID 重投，不再重复计数。
                return;
            }
            throw new RuntimeAdmissionBusyException();
        }
        if (total >= globalLimit || userCount >= perUserLimit) {
            throw new RuntimeAdmissionBusyException();
        }

        int changed = existing == null
                ? mapper.createSlot(userId, bindingId, commandId, action, claimCursor)
                : mapper.activateSlot(userId, bindingId, commandId, action, claimCursor);
        if (changed != 1 || mapper.increment(GLOBAL_POOL) != 1
                || mapper.increment(userPool) != 1) {
            throw new IllegalStateException("原生运行容量占位和计数写入不一致");
        }
        log.info("原生运行占位已获取 commandId={} bindingId={}", commandId, bindingId);
    }

    /**
     * 先按照全局、用户、Binding 的统一锁序绑定回执。
     * 普通提交若已有本次 Turn 的持久结束水位，说明结束事件早于回执到达；
     * 在同一事务内补偿释放。RESUME/RESPOND 无法仅凭旧 Turn 断言新 Run 已结束。
     */
    @Transactional
    public void bindReceipt(String userId, String bindingId, String commandId,
                            String action, String turnId) {
        if (!enabled || "STOP".equals(action)) {
            return;
        }

        String userPool = poolKey(userId);
        lockPools(userPool);
        RuntimeAdmissionSlotDO slot = mapper.lockSlot(bindingId);
        if (slot == null || !slot.active() || !userId.equals(slot.userId())
                || !commandId.equals(slot.commandId())) {
            log.debug("命令回执未命中活动容量占位 commandId={} bindingId={}", commandId, bindingId);
            return;
        }

        String resolvedTurn = turnId;
        if (resolvedTurn == null && "RESPOND".equals(action)) {
            resolvedTurn = mapper.findPriorTurn(userId, bindingId);
        }
        if (resolvedTurn == null || resolvedTurn.isBlank()) {
            log.warn("原生运行回执缺少可核对 Turn，暂保留占位 commandId={} bindingId={}",
                    commandId, bindingId);
            return;
        }
        if (slot.turnId() != null && !slot.turnId().equals(resolvedTurn)) {
            throw new IllegalStateException("相同命令的原生 Turn 与已冻结占位不一致");
        }
        if (mapper.bindTurn(userId, bindingId, commandId, resolvedTurn) != 1) {
            throw new IllegalStateException("原生运行回执绑定时占位状态已变化");
        }

        if (SUBMISSION_ACTIONS.contains(action)
                && resolvedTurn.equals(mapper.findLastProjectedEndedTurn(userId, bindingId))) {
            // 新提交的原生 Turn 已在收据到达前结束；不会根据旧 RESUME/RESPOND Turn 自动放行。
            releaseSlot(slot, userPool);
            log.info("延迟回执已按持久结束事实补偿释放 commandId={} bindingId={}",
                    commandId, bindingId);
        }
    }

    /**
     * 原生 run/start 到达后，为恢复/交互答复占位记录本次新 Run。
     * 仅识别领取时原生投影水位之后的事件，操作与事件投影同事务。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRunStarted(String userId, String bindingId, String runId, long sequence) {
        if (!enabled || runId == null || runId.isBlank() || sequence <= 0) {
            return;
        }
        if (mapper.bindRecoveryRun(userId, bindingId, runId, sequence) == 1) {
            log.info("恢复类原生运行已关联 bindingId={} runId={}", bindingId, runId);
        }
    }

    /**
     * 在原生事件投影水位事务内核对并释放 Slot。
     * 普通提交按 Turn 识别；恢复/答复必须匹配新 Run ID。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseEnded(String userId, String bindingId, String turnId, String runId) {
        if (!enabled || (turnId == null && runId == null)) {
            return;
        }
        String userPool = poolKey(userId);
        lockPools(userPool);

        RuntimeAdmissionSlotDO slot = mapper.lockSlot(bindingId);
        if (slot == null || !slot.active() || !userId.equals(slot.userId())) {
            return;
        }
        if (RECOVERY_ACTIONS.contains(slot.action())) {
            if (runId != null && !runId.isBlank() && runId.equals(slot.runId())) {
                releaseSlot(slot, userPool);
            }
            return;
        }
        if (!SUBMISSION_ACTIONS.contains(slot.action()) || turnId == null || turnId.isBlank()) {
            return;
        }
        String expectedTurn = slot.turnId();
        if (expectedTurn == null) {
            expectedTurn = mapper.findCommandTurn(userId, slot.commandId());
        }
        if (!turnId.equals(expectedTurn)) {
            return;
        }
        releaseSlot(slot, userPool);
    }

    /**
     * 只对未调用 SDK 的确定性拒绝执行进行释放；外部提交结果未知时不能调用。
     */
    @Transactional
    public void releaseUnsubmitted(String userId, String bindingId, String commandId) {
        if (!enabled) {
            return;
        }
        String userPool = poolKey(userId);
        lockPools(userPool);

        RuntimeAdmissionSlotDO slot = mapper.lockSlot(bindingId);
        if (slot == null || !slot.active() || !userId.equals(slot.userId())
                || !commandId.equals(slot.commandId()) || slot.turnId() != null) {
            return;
        }
        releaseSlot(slot, userPool);
    }

    /**
     * 周期性只读巡检全局与用户计数；发现漂移只告警，不在原生事实未知时重建或释放占位。
     */
    @Scheduled(fixedDelayString = "${agentsquad.delivery.admission.audit-delay-ms:60000}")
    public void auditCounters() {
        if (!enabled) {
            return;
        }
        int limit = Math.min(Math.max(auditMaxPools, 1), 100);
        List<String> mismatches = mapper.findCounterMismatches(limit);
        if (!mismatches.isEmpty()) {
            log.error("持久运行占位与配额计数存在差异 mismatchPools={} truncated={}",
                    mismatches.size(), mismatches.size() == limit);
        }
    }

    private void releaseSlot(RuntimeAdmissionSlotDO slot, String userPool) {
        if (mapper.releaseSlot(slot.userId(), slot.bindingId(), slot.commandId()) != 1
                || mapper.decrement(userPool) != 1
                || mapper.decrement(GLOBAL_POOL) != 1) {
            throw new IllegalStateException("原生运行占位回收和计数写入不一致");
        }
        log.info("原生运行占位已释放 commandId={} bindingId={}", slot.commandId(), slot.bindingId());
    }

    private void lockPools(String userPool) {
        Integer global = mapper.lockCounter(GLOBAL_POOL);
        Integer user = mapper.lockCounter(userPool);
        if (global == null || user == null) {
            throw new IllegalStateException("容量计数记录缺失，拒绝错误释放运行占位");
        }
    }

    private String poolKey(String userId) {
        return "USER:" + userId;
    }

    private void requireConfigured() {
        if (globalLimit < 1 || perUserLimit < 1 || perUserLimit > globalLimit) {
            throw new IllegalStateException("运行容量上限配置无效");
        }
    }
}
