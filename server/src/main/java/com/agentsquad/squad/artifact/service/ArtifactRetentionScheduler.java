package com.agentsquad.squad.artifact.service;

import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 显式启用的产物保留清理调度。
 * <p>
 * 默认不触碰实际数据库内容；到期下载已由读入口拒绝，运维启用后周期回收内容。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.artifact.retention", name = "enabled", havingValue = "true")
public class ArtifactRetentionScheduler {
    @Resource
    private ArtifactRetentionService retention;

    @Scheduled(fixedDelayString = "${agentsquad.artifact.retention.interval-ms:3600000}")
    public void purgeExpired() {
        retention.purgeExpired();
    }
}
