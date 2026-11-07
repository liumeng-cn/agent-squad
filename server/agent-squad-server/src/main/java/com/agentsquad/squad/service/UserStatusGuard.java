package com.agentsquad.squad.service;

import com.agentsquad.squad.identity.UserStatusMapper;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.enums.identity.AccountStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 账号停用的运行时安全点。
 *
 * <p>在受理、领取、执行授权、模型调用与外部派发前读取共享身份库；
 * 停用或缺失账号拒绝新的受控动作，不迁移既有会话数据，也不改写已进入 IN_FLIGHT 的账本记录。
 * 生产 profile 必须开启本检查，本地单机模式可显式关闭。</p>
 */
@Service
@Slf4j
public class UserStatusGuard {

    @Resource
    private UserStatusMapper mapper;

    @Value("${agentsquad.identity.enforce-user-status:false}")
    private boolean enabled;

    /**
     * 账号必须存在且为 ACTIVE；停用、删除或未知账号统一拒绝，避免异步任务在停用后继续产生副作用。
     */
    public void requireActive(String userId) {
        if (!enabled) {
            return;
        }
        AccountStatus status = userId == null || userId.isBlank() ? null : mapper.findStatus(userId);
        if (status != AccountStatus.ACTIVE) {
            log.warn("账号状态阻止新的受控动作 userId={} status={}", userId, status == null ? "MISSING" : status);
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "账号已停用或不可用");
        }
    }
}
