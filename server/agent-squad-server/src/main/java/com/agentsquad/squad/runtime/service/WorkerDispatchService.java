package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.runtime.domain.WorkerLaunchRow;
import com.agentsquad.squad.runtime.repository.WorkerDispatchMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * READY Worker 的业务 guard 领取与续租。
 *
 * <p>固定 Binding 可在进程故障后接管；旧令牌失效后不能提交结果候选。</p>
 */
@Service
public class WorkerDispatchService {

    private static final int GUARD_LEASE_SECONDS = 180;

    @Resource
    private WorkerDispatchMapper dispatchMapper;

    /**
     * CAS 领取单个 Job，失败说明其他实例持有有效 guard 或计划已失效。
     */
    @Transactional
    public WorkerLaunchRow claim(String userId, String bindingId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String guardToken = UUID.randomUUID().toString();
        if (dispatchMapper.claimGuard(userId, bindingId, guardToken, now,
                now.plusSeconds(GUARD_LEASE_SECONDS)) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker 节点尚不可领取或已有有效执行者");
        }
        return dispatchMapper.findByBinding(userId, bindingId);
    }

    /**
     * 仅当前令牌可续租；租约过期、取消屏障或被接管后停止继续运行。
     */
    public void renew(String userId, String bindingId, String guardToken) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (dispatchMapper.renewGuard(userId, bindingId, guardToken, now,
                now.plusSeconds(GUARD_LEASE_SECONDS)) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker guard 已失效");
        }
    }
}
