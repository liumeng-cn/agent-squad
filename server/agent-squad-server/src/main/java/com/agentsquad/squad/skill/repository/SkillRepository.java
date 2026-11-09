package com.agentsquad.squad.skill.repository;

import com.agentsquad.squad.domain.entity.SkillGrantDO;
import com.agentsquad.squad.domain.entity.SkillReferenceDO;
import com.agentsquad.squad.domain.entity.SkillVersionDO;
import com.agentsquad.squad.domain.entity.SkillTextFileDO;
import com.agentsquad.squad.mapper.SkillMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 个人技能版本与授权持久化。
 * <p>
 * 只执行带用户归属的数据读写；业务启停、包校验和执行准入由服务层负责。
 */
@Repository
public class SkillRepository {

    @Resource
    private SkillMapper mapper;

    public SkillVersionDO find(String userId, String versionId) {
        return mapper.find(userId, versionId);
    }

    public SkillVersionDO lockVersion(String userId, String versionId) {
        return mapper.lockVersion(userId, versionId);
    }

    public SkillVersionDO findNamedVersion(String userId, String name, String version) {
        return mapper.findNamedVersion(userId, name, version);
    }

    /**
     * 唯一键竞争后使用当前读取得胜出版本，避免 MySQL 可重复读仍返回竞争前的空快照。
     */
    public SkillVersionDO findNamedVersionCurrent(String userId, String name, String version) {
        return mapper.findNamedVersionCurrent(userId, name, version);
    }

    public List<SkillVersionDO> list(String userId, long afterId, int limit) {
        return mapper.list(userId, afterId, limit);
    }

    /**
     * 每个名称只选择最新启用版本；旧执行的引用由独立固定表保存。
     */
    public List<SkillVersionDO> listSelectable(String userId) {
        // 先按稳定顺序锁版本，再锁各版本授权；不使用带快照子查询的 JOIN 推断当前启用版本。
        List<SkillVersionDO> versions = mapper.lockVersions(userId);
        Map<String, SkillVersionDO> selected = new LinkedHashMap<>();
        for (SkillVersionDO version : versions) {
            if (!"ENABLED".equals(version.getStatus())) {
                continue;
            }
            SkillGrantDO grant = lockGrant(userId, version.getSkillVersionId());
            if (grant != null && grant.isEnabled()) {
                selected.put(version.getName(), version);
            }
        }
        return selected.values().stream().sorted(java.util.Comparator.comparing(SkillVersionDO::getName)).toList();
    }

    public void insertVersion(String userId, String versionId, String name, String version, String description,
                              String contentHash, String requiredToolsJson) {
        mapper.insertVersion(userId, versionId, name, version, description, contentHash, requiredToolsJson);
    }

    public void insertFile(String userId, String versionId, SkillTextFileDO file) {
        mapper.insertFile(UUID.randomUUID().toString(), userId, versionId, file);
    }

    public List<SkillTextFileDO> files(String userId, String versionId) {
        return mapper.files(userId, versionId);
    }

    public SkillTextFileDO file(String userId, String versionId, String path) {
        return mapper.file(userId, versionId, path);
    }

    public void insertGrant(String userId, String versionId) {
        mapper.insertGrant(UUID.randomUUID().toString(), userId, versionId);
    }

    public SkillGrantDO grant(String userId, String versionId) {
        return mapper.grant(userId, versionId);
    }

    private SkillGrantDO lockGrant(String userId, String versionId) {
        return mapper.lockGrant(userId, versionId);
    }

    public int changeGrant(String userId, String versionId, long expectedVersion, boolean enabled) {
        return mapper.changeGrant(userId, versionId, expectedVersion, enabled);
    }

    public int revoke(String userId, String versionId) {
        return mapper.revoke(userId, versionId);
    }

    public boolean catalogExists(String userId, String scopeId) {
        return !mapper.catalogExists(userId, scopeId).isEmpty();
    }

    public void insertCatalog(String userId, String scopeId) {
        mapper.insertCatalog(UUID.randomUUID().toString(), userId, scopeId);
    }

    public void insertReference(String userId, String scopeId, SkillVersionDO skill) {
        mapper.insertReference(UUID.randomUUID().toString(), userId, scopeId, skill);
    }

    public List<SkillReferenceDO> references(String userId, String scopeId) {
        return mapper.references(userId, scopeId);
    }

}
