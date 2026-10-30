package com.agentsquad.squad.artifact.service;

import com.agentsquad.squad.artifact.repository.ArtifactDeliveryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产物到期后的引用清理与物理内容回收。
 * <p>
 * 先禁读、清除运行关联，再清空内容；业务摘要和审计元数据保留，避免删除后同键重放造新文件。
 */
@Service
public class ArtifactRetentionService {
    @Resource
    private ArtifactDeliveryMapper mapper;

    @Transactional
    public int purgeExpired() {
        mapper.expireArtifacts();
        mapper.clearScopeReferences();
        mapper.clearTaskReferences();
        mapper.clearMessageReferences();
        return mapper.purgeContents() + mapper.purgeStages();
    }
}
