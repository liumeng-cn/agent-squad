package com.agentsquad.squad.skill.repository;

import com.agentsquad.squad.domain.entity.SkillGrantRow;
import com.agentsquad.squad.domain.entity.SkillReference;
import com.agentsquad.squad.domain.entity.SkillVersionRow;
import com.agentsquad.squad.domain.entity.SkillTextFile;
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

    public SkillVersionRow find(String userId, String versionId) {
        return mapper.find(userId, versionId);
    }

    public SkillVersionRow lockVersion(String userId, String versionId) {
        return mapper.lockVersion(userId, versionId);
    }

    public SkillVersionRow findNamedVersion(String userId, String name, String version) {
        return mapper.findNamedVersion(userId, name, version);
    }

    /**
     * 唯一键竞争后使用当前读取得胜出版本，避免 MySQL 可重复读仍返回竞争前的空快照。
     */
    public SkillVersionRow findNamedVersionCurrent(String userId, String name, String version) {
        return mapper.findNamedVersionCurrent(userId, name, version);
    }

    public List<SkillVersionRow> list(String userId, long afterId, int limit) {
        return mapper.list(userId, afterId, limit);
    }

    /**
     * 每个名称只选择最新启用版本；旧执行的引用由独立固定表保存。
     */
    public List<SkillVersionRow> listSelectable(String userId) {
        // 先按稳定顺序锁版本，再锁各版本授权；不使用带快照子查询的 JOIN 推断当前启用版本。
        List<SkillVersionRow> versions = mapper.lockVersions(userId);
        Map<String, SkillVersionRow> selected = new LinkedHashMap<>();
        for (SkillVersionRow version : versions) {
            if (!"ENABLED".equals(version.status())) {
                continue;
            }
            SkillGrantRow grant = lockGrant(userId, version.skillVersionId());
            if (grant != null && grant.enabled()) {
                selected.put(version.name(), version);
            }
        }
        return selected.values().stream().sorted(java.util.Comparator.comparing(SkillVersionRow::name)).toList();
    }

    public void insertVersion(String userId, String versionId, String name, String version, String description,
                              String contentHash, String requiredToolsJson) {
        mapper.insertVersion(userId, versionId, name, version, description, contentHash, requiredToolsJson);
    }

    public void insertFile(String userId, String versionId, SkillTextFile file) {
        mapper.insertFile(UUID.randomUUID().toString(), userId, versionId, file);
    }

    public List<SkillTextFile> files(String userId, String versionId) {
        return mapper.files(userId, versionId);
    }

    public SkillTextFile file(String userId, String versionId, String path) {
        return mapper.file(userId, versionId, path);
    }

    public void insertGrant(String userId, String versionId) {
        mapper.insertGrant(UUID.randomUUID().toString(), userId, versionId);
    }

    public SkillGrantRow grant(String userId, String versionId) {
        return mapper.grant(userId, versionId);
    }

    private SkillGrantRow lockGrant(String userId, String versionId) {
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

    public void insertReference(String userId, String scopeId, SkillVersionRow skill) {
        mapper.insertReference(UUID.randomUUID().toString(), userId, scopeId, skill);
    }

    public List<SkillReference> references(String userId, String scopeId) {
        return mapper.references(userId, scopeId);
    }

}
