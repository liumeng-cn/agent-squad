package com.agentsquad.squad.sandbox.service;

import com.agentsquad.squad.sandbox.repository.SandboxMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 沙箱失联执行的持久恢复分类。
 * <p>
 * 超过截止时间只转UNKNOWN并保留预算，不以新容器重发旧原生调用或发布不完整产物。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "agentsquad.sandbox", name = "enabled", havingValue = "true")
public class SandboxRecoveryScheduler {
    @Resource
    private SandboxMapper mapper;

    @Resource
    private SandboxRunTransaction transactions;

    @Scheduled(fixedDelayString = "${agentsquad.sandbox.recovery-interval-ms:30000}")
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
