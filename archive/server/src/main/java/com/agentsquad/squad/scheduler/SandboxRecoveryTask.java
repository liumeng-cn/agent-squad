package com.agentsquad.squad.scheduler;


import com.agentsquad.squad.service.SandboxRunTransactionService;

import com.agentsquad.squad.mapper.SandboxMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 沙箱失联执行核对定时任务
 *
 * <p>扫描超过截止时间的沙箱执行，将失联记录转入待核对状态并保留预算。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.sandbox", name = "enabled", havingValue = "true")
public class SandboxRecoveryTask {
    @Resource
    private SandboxMapper mapper;

    @Resource
    private SandboxRunTransactionService transactions;

    /**
     * 逐条交由沙箱事务服务核对截止时间和当前状态，再标记为未知。
     */
    public void markUnknown() {
        int count = 0;
        for (var run : mapper.expired()) {
            count += transactions.expire(run);
        }
        if (count > 0) {
            log.warn("沙箱失联执行已进入核对状态 count={} reason=deadline_exceeded", count);
        }
    }
}
