package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.conversation.domain.RuntimeAdmissionSlot;
import com.agentsquad.squad.conversation.repository.RuntimeAdmissionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原生运行持久容量管理。
 *
 * 领取新运行命令时在 MySQL 事务内同时占用全局和用户配额，
 * 只在匹配的原生 run/end 已持久投影时释放；未知结果保守保留，
 * 不把短期命令投递租约视为运行结束事实。
 */
@Service
@Slf4j
public class RuntimeAdmissionService {

    private static final String GLOBAL_POOL = "GLOBAL";
    private static final String ACTIVE = "ACTIVE";

    @Resource
    private RuntimeAdmissionMapper mapper;

    @Value("${agentsquad.delivery.admission.enabled:false}")
    private boolean enabled;

    @Value("${agentsquad.delivery.admission.global-limit:12}")
    private int globalLimit;

    @Value("${agentsquad.delivery.admission.per-user-limit:2}")
    private int perUserLimit;

    /**
     * 与 CommandClaimService.claim 共用事务；抛出容量异常时整个命令领取回滚。
     * STOP 只负责停止原有运行，不占用新的执行配额。
     */
    public void acquire(String userId, String bindingId, String commandId, String action) {
        if (!enabled || "STOP".equals(action)) {
            return;
        }
        requireConfigured();
        if (userId == null || bindingId == null || commandId == null) {
            throw new IllegalStateException("运行占位缺少用户或命令归属");
        }

        String userPool = poolKey(userId);
        mapper.ensureCounter(GLOBAL_POOL);
        mapper.ensureCounter(userPool);

        Integer total = mapper.lockCounter(GLOBAL_POOL);
        Integer userCount = mapper.lockCounter(userPool);
        RuntimeAdmissionSlot existing = mapper.lockSlot(bindingId);

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
                ? mapper.createSlot(userId, bindingId, commandId, action)
                : mapper.activateSlot(userId, bindingId, commandId, action);
        if (changed != 1 || mapper.increment(GLOBAL_POOL) != 1
                || mapper.increment(userPool) != 1) {
            throw new IllegalStateException("原生运行容量占位和计数写入不一致");
        }
        log.info("原生运行占位已获取 commandId={} bindingId={}", commandId, bindingId);
    }

    /**
     * 收到原生投递回执后关联真实 Turn，不立即释放执行容量。
     * 兼容同命令重投；未知回执保持原占位供下一次投影或运维核对。
     */
    @Transactional
    public void bindReceipt(String userId, String bindingId, String commandId,
                            String action, String turnId) {
        if (!enabled || "STOP".equals(action)) {
            return;
        }
        String resolvedTurn = turnId;
        if (resolvedTurn == null && "RESPOND".equals(action)) {
            resolvedTurn = mapper.findPriorTurn(userId, bindingId);
        }
        if (resolvedTurn == null || resolvedTurn.isBlank()) {
            log.warn("原生运行占位缺少可核对 Turn，暂不释放 commandId={} bindingId={}", commandId, bindingId);
            return;
        }
        int changed = mapper.bindTurn(userId, bindingId, commandId, resolvedTurn);
        if (changed != 1) {
            // 已结束回合或并发恢复期间可能已经释放，占位归属无法确认时拒绝修改其他命令。
            log.warn("原生运行占位绑定回执未命中 commandId={} bindingId={}", commandId, bindingId);
        }
    }

    /**
     * 必须在 NativeProjectionService.accept 的原生日志水位事务内调用。
     * 若投递回执尚未来得及绑定，则从原命令持久收据中补查 Turn。
     */
    public void releaseEnded(String userId, String bindingId, String turnId) {
        if (!enabled || turnId == null || turnId.isBlank()) {
            return;
        }
        String userPool = poolKey(userId);
        lockPools(userPool);

        RuntimeAdmissionSlot slot = mapper.lockSlot(bindingId);
        if (slot == null || !slot.active() || !userId.equals(slot.userId())) {
            return;
        }
        String expectedTurn = slot.turnId();
        if (expectedTurn == null) {
            expectedTurn = mapper.findCommandTurn(userId, slot.commandId());
        }
        if (expectedTurn == null && "RESPOND".equals(slot.action())) {
            expectedTurn = mapper.findPriorTurn(userId, bindingId);
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

        RuntimeAdmissionSlot slot = mapper.lockSlot(bindingId);
        if (slot == null || !slot.active() || !userId.equals(slot.userId())
                || !commandId.equals(slot.commandId()) || slot.turnId() != null) {
            return;
        }
        releaseSlot(slot, userPool);
    }

    private void releaseSlot(RuntimeAdmissionSlot slot, String userPool) {
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
