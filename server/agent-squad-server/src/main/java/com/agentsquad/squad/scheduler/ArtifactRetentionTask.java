package com.agentsquad.squad.scheduler;


import com.agentsquad.squad.service.ArtifactRetentionService;

import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 过期产物清理定时任务
 *
 * <p>产物保留功能启用后，由产物保留服务执行过期清理。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.artifact.retention", name = "enabled", havingValue = "true")
public class ArtifactRetentionTask {
    @Resource
    private ArtifactRetentionService retention;

    /**
     * 触发一轮过期产物清理，具体清理范围由产物保留服务确定。
     */
    public void purgeExpired() {
        retention.purgeExpired();
    }
}
